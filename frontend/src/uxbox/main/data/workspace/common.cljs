(ns uxbox.main.data.workspace.common
  (:require
   [clojure.set :as set]
   [cljs.spec.alpha :as s]
   [beicon.core :as rx]
   [potok.core :as ptk]
   [uxbox.main.geom :as geom]
   [uxbox.common.data :as d]
   [uxbox.common.spec :as us]
   [uxbox.common.pages :as cp]
   [uxbox.common.uuid :as uuid]))

;; --- Protocols
(defprotocol IBatchedChange)
(defprotocol IUpdateGroup
  (get-ids [this]))

(defn calculate-frame-overlap
  [objects shape]
  (let [rshp (geom/shape->rect-shape shape)

        xfmt (comp
              (filter #(= :frame (:type %)))
              (filter #(not= (:id shape) (:id %)))
              (filter #(not= uuid/zero (:id %)))
              (filter #(geom/overlaps? % rshp)))

        frame (->> (vals objects)
                   (sequence xfmt)
                   (first))]

    (or (:id frame) uuid/zero)))

(s/def ::undo-changes ::cp/changes)
(s/def ::redo-changes ::cp/changes)
(s/def ::undo-entry
  (s/keys :req-un [::undo-changes ::redo-changes]))

(def MAX-UNDO-SIZE 50)

(defn conj-undo-entry
  [undo data]
  (let [undo (conj undo data)]
    (if (> (count undo) MAX-UNDO-SIZE)
      (into [] (take MAX-UNDO-SIZE undo))
      undo)))

(defn materialize-undo
  [changes index]
  (ptk/reify ::materialize-undo
    ptk/UpdateEvent
    (update [_ state]
      (let [page-id (:page-id state)]
        (-> state
            (update-in [:workspace-data page-id] cp/process-changes changes)
            (assoc-in [:workspace-local :undo-index] index))))))

(defn reset-undo
  [index]
  (ptk/reify ::reset-undo
    ptk/UpdateEvent
    (update [_ state]
      (-> state
          (update :workspace-local dissoc :undo-index)
          (update-in [:workspace-local :undo]
                     (fn [queue]
                       (into [] (take (inc index) queue))))))))
(defn append-undo
  [entry]
  (us/verify ::undo-entry entry)
  (ptk/reify ::append-undo
    ptk/UpdateEvent
    (update [_ state]
      (update-in state [:workspace-local :undo] (fnil conj-undo-entry []) entry))))



(defn commit-changes
  ([changes undo-changes] (commit-changes changes undo-changes {}))
  ([changes undo-changes {:keys [save-undo?
                                 commit-local?]
                          :or {save-undo? true
                               commit-local? false}
                          :as opts}]
   (us/verify ::cp/changes changes)
   (us/verify ::cp/changes undo-changes)

   (ptk/reify ::commit-changes
     cljs.core/IDeref
     (-deref [_] changes)

     ptk/UpdateEvent
     (update [_ state]
       (let [page-id (:page-id state)
             state (update-in state [:pages-data page-id] cp/process-changes changes)]
         (cond-> state
           commit-local? (update-in [:workspace-data page-id] cp/process-changes changes))))

     ptk/WatchEvent
     (watch [_ state stream]
       (let [page (:workspace-page state)
             uidx (get-in state [:workspace-local :undo-index] ::not-found)]
         (rx/concat
          (when (and save-undo? (not= uidx ::not-found))
            (rx/of (reset-undo uidx)))

          (when save-undo?
            (let [entry {:undo-changes undo-changes
                         :redo-changes changes}]
              (rx/of (append-undo entry))))))))))

(defn rehash-shape-frame-relationship
  [ids]
  (letfn [(impl-diff [state]
            (loop [id  (first ids)
                   ids (rest ids)
                   rch []
                   uch []]
              (if (nil? id)
                [rch uch]
                (let [pid (:page-id state)
                      objects (get-in state [:workspace-data pid :objects])
                      obj (get objects id)
                      fid (calculate-frame-overlap objects obj)]
                  (if (not= fid (:frame-id obj))
                    (recur (first ids)
                           (rest ids)
                           (conj rch {:type :mov-obj
                                      :id id
                                      :frame-id fid})
                           (conj uch {:type :mov-obj
                                      :id id
                                      :frame-id (:frame-id obj)}))
                    (recur (first ids)
                           (rest ids)
                           rch
                           uch))))))]
    (ptk/reify ::rehash-shape-frame-relationship
      ptk/WatchEvent
      (watch [_ state stream]
        (let [[rch uch] (impl-diff state)]
          (when-not (empty? rch)
            (rx/of (commit-changes rch uch {:commit-local? true}))))))))

(defn- generate-operations
  [ma mb]
  (let [ma-keys (set (keys ma))
        mb-keys (set (keys mb))
        added (set/difference mb-keys ma-keys)
        removed (set/difference ma-keys mb-keys)
        both (set/intersection ma-keys mb-keys)]
    (d/concat
     (mapv #(array-map :type :set :attr % :val (get mb %)) added)
     (mapv #(array-map :type :set :attr % :val nil) removed)
     (loop [k (first both)
            r (rest both)
            rs []]
       (if k
         (let [vma (get ma k)
               vmb (get mb k)]
           (if (= vma vmb)
             (recur (first r) (rest r) rs)
             (recur (first r) (rest r) (conj rs {:type :set
                                                 :attr k
                                                 :val vmb}))))
         rs)))))

(defn- generate-changes
  [prev curr]
  (letfn [(impl-diff [res id]
            (let [prev-obj (get-in prev [:objects id])
                  curr-obj (get-in curr [:objects id])
                  ops (generate-operations (dissoc prev-obj :shapes :frame-id)
                                           (dissoc curr-obj :shapes :frame-id))]
              (if (empty? ops)
                res
                (conj res {:type :mod-obj
                           :operations ops
                           :id id}))))]
    (reduce impl-diff [] (set/union (set (keys (:objects prev)))
                                    (set (keys (:objects curr)))))))

(defn diff-and-commit-changes
  [page-id]
  (ptk/reify ::diff-and-commit-changes
    ptk/WatchEvent
    (watch [_ state stream]
      (let [page-id (:page-id state)
            curr (get-in state [:workspace-data page-id])
            prev (get-in state [:pages-data page-id])

            changes (generate-changes prev curr)
            undo-changes (generate-changes curr prev)]
        (when-not (empty? changes)
          (rx/of (commit-changes changes undo-changes)))))))
