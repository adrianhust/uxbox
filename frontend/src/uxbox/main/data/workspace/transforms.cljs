(ns uxbox.main.data.workspace.transforms
  "Events related with shapes transformations"
  (:require
   [cljs.spec.alpha :as s]
   [beicon.core :as rx]
   [potok.core :as ptk]
   [uxbox.common.spec :as us]
   [uxbox.common.data :as d]
   [uxbox.main.refs :as refs]
   [uxbox.main.store :as st]
   [uxbox.main.streams :as ms]
   [uxbox.main.geom :as geom]
   [uxbox.util.geom.point :as gpt]
   [uxbox.util.geom.matrix :as gmt]
   [uxbox.main.data.helpers :as helpers]
   [uxbox.main.data.workspace.common :refer [IBatchedChange IUpdateGroup] :as common]))
;; -- Specs

(s/def ::set-of-uuid
  (s/every uuid? :kind set?))

;; -- Declarations

(declare assoc-resize-modifier-in-bulk)
(declare apply-displacement-in-bulk)
(declare apply-frame-displacement)
(declare apply-rotation)

(declare materialize-resize-modifier-in-bulk)
(declare materialize-displacement-in-bulk)
(declare materialize-frame-displacement)
(declare materialize-rotation)

(defn- apply-zoom
  [point]
  (gpt/divide point (gpt/point @refs/selected-zoom)))

;; -- RESIZE
(defn start-resize
  [vid ids shape objects]
  (letfn [(resize [shape initial [point lock?]]
            (let [frame (get objects (:frame-id shape))
                  ;; result (geom/resize-shape vid shape initial point lock?)
                  ;; scale (geom/calculate-scale-ratio shape result)
                  {:keys [width height rotation]} shape
                  p (fn [ss it] (println ss it) it)

                  center (gpt/center shape)

                  shapev (-> (gpt/point width height)
                             (gpt/transform  (gmt/rotate-matrix (- rotation)))) 
                  _ (println "Points" point initial)

                  
                  
                  deltav (as->(p " > 1" (gpt/subtract point initial)) $
                           (p " > 2" (gpt/transform $ (gmt/rotate-matrix (- rotation))))
                           (p " > 3" (gpt/multiply $ (gpt/point 1.0 0.0)))
                           #_(p " > 4" (gpt/transform $ (gmt/rotate-matrix rotation))))
                 
                  {scalex :x scaley :y :as scalev} (gpt/divide (gpt/add shapev deltav) shapev)
                  _ (println "Shapev" shapev)
                  _ (println "Delta" deltav)
                  _ (println "Scale" scalev)
                  
                  resize-matrix (geom/generate-resize-matrix vid shape frame rotation [scalex scaley])
                  rotation (:rotation shape)
                  displacement-matrix (gmt/correct-rotation vid width height scalex scaley rotation)
                  _ (println "Resize" resize-matrix)
                  _ (println "Resize 2" (-> (gmt/matrix)
                                            (gmt/rotate rotation)
                                            (gmt/multiply resize-matrix)
                                            (gmt/rotate  (- rotation))))
                  _ (println "Displacement" resize-matrix)]
              (rx/of (assoc-resize-modifier-in-bulk ids resize-matrix displacement-matrix))))

          ;; Unifies the instantaneous proportion lock modifier
          ;; activated by Ctrl key and the shapes own proportion
          ;; lock flag that can be activated on element options.
          (normalize-proportion-lock [[point ctrl?]]
            (let [proportion-lock? (:proportion-lock shape)]
              [point (or proportion-lock? ctrl?)]))

          ;; Applies alginment to point if it is currently
          ;; activated on the current workspace
          ;; (apply-grid-alignment [point]
          ;;   (if @refs/selected-alignment
          ;;     (uwrk/align-point point)
          ;;     (rx/of point)))
          ]
    (reify
      ptk/WatchEvent
      (watch [_ state stream]
        (let [initial @ms/mouse-position
              shape  (geom/shape->rect-shape shape)
              stoper (rx/filter ms/mouse-up? stream)]
          (rx/concat
           (->> ms/mouse-position
                (rx/map apply-zoom)
                ;; (rx/mapcat apply-grid-alignment)
                (rx/with-latest vector ms/mouse-position-ctrl)
                (rx/map normalize-proportion-lock)
                (rx/mapcat (partial resize shape initial))
                (rx/take-until stoper))
           (rx/of (materialize-resize-modifier-in-bulk ids))))))))

(defn assoc-resize-modifier-in-bulk
  [ids resize-matrix displacement-matrix]
  (us/verify ::set-of-uuid ids)
  (us/verify gmt/matrix? resize-matrix)
  (us/verify gmt/matrix? displacement-matrix)
  (ptk/reify ::assoc-resize-modifier-in-bulk
    ptk/UpdateEvent
    (update [_ state]
      (let [page-id (:page-id state)
            objects (get-in state [:workspace-data page-id :objects])
            rfn #(-> %1
                     (assoc-in [:workspace-data page-id :objects %2 :resize-modifier] resize-matrix)
                     (assoc-in [:workspace-data page-id :objects %2 :displacement-modifier] displacement-matrix))
            ;; TODO: REMOVE FRAMES FROM IDS TO PROPAGATE
            ids-with-children (concat ids (mapcat #(helpers/get-children % objects) ids))]
        (reduce rfn state ids-with-children)))))

(defn materialize-resize-modifier-in-bulk
  [ids]
  (ptk/reify ::materialize-resize-modifier-in-bulk

    IUpdateGroup
    (get-ids [_] ids)

    ptk/UpdateEvent
    (update [_ state]
      (let [page-id (:page-id state)
            objects (get-in state [:workspace-data page-id :objects])

            ;; Updates the resize data for a single shape
            materialize-shape
            (fn [state id]
              (update-in state [:workspace-data page-id :objects id] geom/transform-shape))

            ;; Applies materialize-shape over shape children
            materialize-children
            (fn [state id]
              (reduce materialize-shape state (helpers/get-children id objects)))

            ;; For each shape makes permanent the displacemnt
            update-shapes
            (fn [state id]
              (let [shape (-> (get objects id) geom/transform-shape)]
                (-> state
                    (materialize-shape id)
                    (materialize-children id))))]

        #_state
        (reduce update-shapes state ids)))

    ptk/WatchEvent
    (watch [_ state stream]
      (let [page-id (:page-id state)]
        (rx/of (common/diff-and-commit-changes page-id)
               (common/rehash-shape-frame-relationship ids))))))


;; -- ROTATE
(defn start-rotate
  [shapes]
  (ptk/reify ::start-rotate
    ptk/WatchEvent
    (watch [_ state stream]
      (let [stoper (rx/filter ms/mouse-up? stream)
            group  (geom/selection-rect shapes)
            _ (.log js/console "GROUP" (clj->js group))
            group-center (gpt/center group)
            _ (.log js/console "CENTER" (clj->js group-center))
            initial-angle (gpt/angle @ms/mouse-position group-center)
            _ (.log js/console "INITIAL" (clj->js initial-angle))
            calculate-angle (fn [pos ctrl?]
                              (let [angle (- (gpt/angle pos group-center) initial-angle)
                                    angle (if (neg? angle) (+ 360 angle) angle)
                                    modval (mod angle 90)
                                    angle (if ctrl?
                                            (if (< 50 modval)
                                              (+ angle (- 90 modval))
                                              (- angle modval))
                                            angle)
                                    angle (if (= angle 360)
                                            0
                                            angle)]
                                angle))]
        (rx/concat
         (->> ms/mouse-position
              (rx/map apply-zoom)
              (rx/with-latest vector ms/mouse-position-ctrl)
              (rx/map (fn [[pos ctrl?]]
                        (let [delta-angle (calculate-angle pos ctrl?)]
                          (apply-rotation delta-angle shapes))))
              
              
              (rx/take-until stoper))
         (rx/of (materialize-rotation shapes))
         )))))

;; -- MOVE

(defn start-move-selected []
  (ptk/reify ::start-move-selected
    ptk/WatchEvent
    (watch [_ state stream]
      (let [selected (get-in state [:workspace-local :selected])
            stoper (rx/filter ms/mouse-up? stream)
            zero-point? #(= % (gpt/point 0 0))
            position @ms/mouse-position]
        (rx/concat
         (->> (ms/mouse-position-deltas position)
              (rx/filter (complement zero-point?))
              (rx/map #(apply-displacement-in-bulk selected %))
              (rx/take-until stoper))
         (rx/of (materialize-displacement-in-bulk selected)))))))

(defn start-move-frame []
  (ptk/reify ::start-move-frame
    ptk/WatchEvent
    (watch [_ state stream]
      (let [selected (get-in state [:workspace-local :selected])
            stoper (rx/filter ms/mouse-up? stream)
            zero-point? #(= % (gpt/point 0 0))
            frame-id (first selected)
            position @ms/mouse-position]
        (rx/concat
         (->> (ms/mouse-position-deltas position)
              (rx/filter (complement zero-point?))
              (rx/map #(apply-frame-displacement frame-id %))
              (rx/take-until stoper))
         (rx/of (materialize-frame-displacement frame-id)))))))

(defn- get-displacement-with-grid
  "Retrieve the correct displacement delta point for the
  provided direction speed and distances thresholds."
  [shape direction options]
  (let [grid-x (:grid-x options 10)
        grid-y (:grid-y options 10)
        x-mod (mod (:x shape) grid-x)
        y-mod (mod (:y shape) grid-y)]
    (case direction
      :up (gpt/point 0 (- (if (zero? y-mod) grid-y y-mod)))
      :down (gpt/point 0 (- grid-y y-mod))
      :left (gpt/point (- (if (zero? x-mod) grid-x x-mod)) 0)
      :right (gpt/point (- grid-x x-mod) 0))))

(defn- get-displacement
  "Retrieve the correct displacement delta point for the
  provided direction speed and distances thresholds."
  [shape direction]
  (case direction
    :up (gpt/point 0 (- 1))
    :down (gpt/point 0 1)
    :left (gpt/point (- 1) 0)
    :right (gpt/point 1 0)))

(defn move-selected
  [direction align?]
  (us/verify ::direction direction)
  (us/verify boolean? align?)

  (ptk/reify ::move-selected
    ptk/WatchEvent
    (watch [_ state stream]
      (let [pid (:page-id state)
            selected (get-in state [:workspace-local :selected])
            options (get-in state [:workspace-data pid :options])
            shapes (map #(get-in state [:workspace-data pid :objects %]) selected)
            shape (geom/shapes->rect-shape shapes)
            displacement (if align?
                           (get-displacement-with-grid shape direction options)
                           (get-displacement shape direction))]
        (rx/of (apply-displacement-in-bulk selected displacement)
               (materialize-displacement-in-bulk selected))))))


;; -- Apply modifiers
(defn apply-displacement-in-bulk
  "Apply the same displacement delta to all shapes identified by the set
  if ids."
  [ids delta]
  (us/verify ::set-of-uuid ids)
  (us/verify gpt/point? delta)
  (ptk/reify ::apply-displacement-in-bulk
    ptk/UpdateEvent
    (update [_ state]
      (let [page-id (:page-id state)
            objects (get-in state [:workspace-data page-id :objects])
            rfn (fn [state id]
                  (let [path [:workspace-data page-id :objects id]
                        shape (get-in state path)
                        prev    (:displacement-modifier shape (gmt/matrix))
                        curr    (gmt/translate prev delta)]
                    (update-in state path #(assoc % :displacement-modifier curr))))
            ids-with-children (concat ids (mapcat #(helpers/get-children % objects) ids))]
        (reduce rfn state ids-with-children)))))


(defn materialize-displacement-in-bulk
  [ids]
  (ptk/reify ::materialize-displacement-in-bulk
    IBatchedChange
    
    IUpdateGroup
    (get-ids [_] ids)

    ptk/UpdateEvent
    (update [_ state]
      (let [page-id (:page-id state)
            objects (get-in state [:workspace-data page-id :objects])

            ;; Updates the displacement data for a single shape
            materialize-shape
            (fn [state id mtx]
              (update-in
               state
               [:workspace-data page-id :objects id]
               #(-> %
                    (dissoc :displacement-modifier)
                    (geom/transform mtx))))

            ;; Applies materialize-shape over shape children
            materialize-children
            (fn [state id mtx]
              (reduce #(materialize-shape %1 %2 mtx) state (helpers/get-children id objects)))

            ;; For each shape makes permanent the resize
            update-shapes
            (fn [state id]
              (let [shape (get objects id)
                    mtx (:displacement-modifier shape (gmt/matrix))]
                (-> state
                    (materialize-shape id mtx)
                    (materialize-children id mtx))))]

        (as-> state $
          (reduce update-shapes $ ids))))

    ptk/WatchEvent
    (watch [_ state stream]
      (let [page-id (:page-id state)]
        (rx/of (common/diff-and-commit-changes page-id)
               (common/rehash-shape-frame-relationship ids))))))

(defn apply-frame-displacement
  "Apply the same displacement delta to all shapes identified by the
  set if ids."
  [id delta]
  (us/verify ::us/uuid id)
  (us/verify gpt/point? delta)
  (ptk/reify ::apply-frame-displacement
    ptk/UpdateEvent
    (update [_ state]
      (let [page-id (:page-id state)]
        (update-in state [:workspace-data page-id :objects id]
                   (fn [shape]
                     (let [prev (:displacement-modifier shape (gmt/matrix))
                           xfmt (gmt/translate prev delta)]
                       (assoc shape :displacement-modifier xfmt))))))))

(defn materialize-frame-displacement
  [id]
  (us/verify ::us/uuid id)
  (ptk/reify ::materialize-frame-displacement
    IBatchedChange
    ptk/UpdateEvent
    (update [_ state]
      (let [page-id (:page-id state)
            objects (get-in state [:workspace-data page-id :objects])
            frame   (get objects id)
            xfmt     (or (:displacement-modifier frame) (gmt/matrix))

            frame   (-> frame
                        (dissoc :displacement-modifier)
                        (geom/transform xfmt))

            shapes  (->> (helpers/get-children id objects)
                         (map #(get objects %))
                         (map #(geom/transform % xfmt))
                         (d/index-by :id))

            shapes (assoc shapes (:id frame) frame)]
        (update-in state [:workspace-data page-id :objects] merge shapes)))))


(defn apply-rotation
  [delta-rotation shapes]
  (ptk/reify ::apply-rotation
    ptk/UpdateEvent
    (update [_ state]
      (let [page-id (:page-id state)]
        (letfn [(calculate-displacement [shape angle center]
                  (let [shape-rect (geom/selection-rect [shape])
                        shape-center (gpt/center shape-rect)]
                    (-> (gmt/matrix)
                        (gmt/rotate angle center)
                        (gmt/rotate (- angle) shape-center))))

                (rotate-shape [state angle shape center]
                  (let [objects (get-in state [:workspace-data page-id :objects])
                        path [:workspace-data page-id :objects (:id shape)]
                        ds (calculate-displacement shape angle center)]
                    (-> state
                        (assoc-in (conj path :rotation-modifier) angle)
                        (assoc-in (conj path :displacement-modifier) ds)
                        (rotate-around-center angle center (map #(get objects %) (:shapes shape))))))


                (rotate-around-center [state angle center shapes]
                  (reduce #(rotate-shape %1 angle %2 center) state shapes))]

          (let [center (-> shapes geom/selection-rect gpt/center)]
            (rotate-around-center state delta-rotation center shapes)))))))

(defn materialize-rotation
  [shapes]
  (ptk/reify ::materialize-rotation
    IBatchedChange
    IUpdateGroup
    (get-ids [_] (map :id shapes))

    ptk/UpdateEvent
    (update [_ state]
      (letfn
          [(apply-rotation [shape]
             (let [ds-modifier (or (:displacement-modifier shape) (gmt/matrix))]
               (-> shape
                   (update :rotation #(mod (+ % (:rotation-modifier shape)) 360))
                   (geom/transform ds-modifier)
                   (dissoc :rotation-modifier)
                   (dissoc :displacement-modifier))))


           (materialize-shape [state shape]
             (let [page-id (:page-id state)
                   objects (get-in state [:workspace-data page-id :objects])
                   path [:workspace-data page-id :objects (:id shape)]]
               (as-> state $
                 (update-in $ path apply-rotation)
                 (reduce materialize-shape $ (map #(get objects %) (:shapes shape))))))]

        (reduce materialize-shape state shapes)))))



