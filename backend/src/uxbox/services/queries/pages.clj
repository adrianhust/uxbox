;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; This Source Code Form is "Incompatible With Secondary Licenses", as
;; defined by the Mozilla Public License, v. 2.0.
;;
;; Copyright (c) 2019-2020 Andrey Antukh <niwi@niwi.nz>

(ns uxbox.services.queries.pages
  (:require
   [clojure.spec.alpha :as s]
   [promesa.core :as p]
   [uxbox.common.spec :as us]
   [uxbox.db :as db]
   [uxbox.services.queries :as sq]
   [uxbox.services.util :as su]
   [uxbox.services.queries.files :as files]
   [uxbox.util.blob :as blob]
   [uxbox.util.sql :as sql]))

;; --- Helpers & Specs

(declare decode-row)

(s/def ::id ::us/uuid)
(s/def ::profile-id ::us/uuid)
(s/def ::project-id ::us/uuid)
(s/def ::file-id ::us/uuid)



;; --- Query: Pages (By File ID)

(declare retrieve-pages)

(s/def ::pages
  (s/keys :req-un [::profile-id ::file-id]))

(sq/defquery ::pages
  [{:keys [profile-id file-id] :as params}]
  (db/with-atomic [conn db/pool]
    (files/check-edition-permissions! conn profile-id file-id)
    (retrieve-pages conn params)))

(def ^:private sql:pages
  "select p.*
     from page as p
    where p.file_id = $1
      and p.deleted_at is null
    order by p.created_at asc")

(defn- retrieve-pages
  [conn {:keys [profile-id file-id] :as params}]
  (-> (db/query conn [sql:pages file-id])
      (p/then (partial mapv decode-row))))



;; --- Query: Single Page (By ID)

(declare retrieve-page)

(s/def ::page
  (s/keys :req-un [::profile-id ::id]))

(sq/defquery ::page
  [{:keys [profile-id id] :as params}]
  (db/with-atomic [conn db/pool]
    (p/let [page (retrieve-page conn id)]
      (files/check-edition-permissions! conn profile-id (:file-id page))
      page)))

(def ^:private sql:page
  "select p.* from page as p where id=$1")

(defn retrieve-page
  [conn id]
  (-> (db/query-one conn [sql:page id])
      (p/then' su/raise-not-found-if-nil)
      (p/then' decode-row)))


;; --- Query: Page Changes

(def ^:private
  sql:page-changes
  "select pc.id,
          pc.created_at,
          pc.changes,
          pc.revn
     from page_change as pc
    where pc.page_id=$1
    order by pc.revn asc
    limit $2
   offset $3")


(s/def ::skip ::us/integer)
(s/def ::limit ::us/integer)

(s/def ::page-changes
  (s/keys :req-un [::profile-id ::id ::skip ::limit]))

(defn retrieve-page-changes
  [conn id skip limit]
  (-> (db/query conn [sql:page-changes id limit skip])
      (p/then' #(mapv decode-row %))))

(sq/defquery ::page-changes
  [{:keys [profile-id id skip limit]}]
  (when *assert*
    (-> (db/query db/pool [sql:page-changes id limit skip])
        (p/then' #(mapv decode-row %)))))


;; --- Helpers

(defn decode-row
  [{:keys [data metadata changes] :as row}]
  (when row
    (cond-> row
      data (assoc :data (blob/decode data))
      changes (assoc :changes (blob/decode changes)))))
