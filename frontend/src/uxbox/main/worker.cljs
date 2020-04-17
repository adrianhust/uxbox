;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; This Source Code Form is "Incompatible With Secondary Licenses", as
;; defined by the Mozilla Public License, v. 2.0.
;;
;; Copyright (c) 2020 UXBOX Labs SL

(ns uxbox.main.worker
  (:require
   [cljs.spec.alpha :as s]
   [uxbox.common.spec :as us]
   [uxbox.util.worker :as uw]))

(defn on-error
  [instance error]
  (js/console.log "error on worker" error))

(defonce instance
  (when (not= *target* "nodejs")
    (uw/init "js/worker.js" on-error)))

(defn ask!
  [message]
  (uw/ask! instance message))
