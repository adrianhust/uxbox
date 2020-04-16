;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2016 Andrey Antukh <niwi@niwi.nz>

(ns uxbox.worker.impl
  (:require [uxbox.util.transit :as t]))

(enable-console-print!)

;; --- Handler

(defmulti handler :cmd)

(defmethod handler :default
  [message]
  (println "Unexpected message:" message))

(defmethod handler :echo
  [message]
  message)
