;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; Copyright (c) 2016 Andrey Antukh <niwi@niwi.nz>

(ns uxbox.ui.auth.recovery
  (:require [sablono.core :as html :refer-macros [html]]
            [lentes.core :as l]
            [cuerdas.core :as str]
            [rum.core :as rum]
            [uxbox.router :as rt]
            [uxbox.state :as st]
            [uxbox.rstore :as rs]
            [uxbox.schema :as us]
            [uxbox.data.auth :as uda]
            [uxbox.data.messages :as udm]
            [uxbox.data.forms :as udf]
            [uxbox.ui.forms :as forms]
            [uxbox.ui.icons :as i]
            [uxbox.ui.messages :as uum]
            [uxbox.ui.navigation :as nav]
            [uxbox.ui.mixins :as mx]
            [uxbox.util.dom :as dom]))

;; --- Constants

(def form-data
  (-> (l/in [:forms :recovery])
      (l/focus-atom st/state)))

(def form-errors
  (-> (l/in [:errors :recovery])
      (l/focus-atom st/state)))

(def set-value!
  (partial udf/assign-field-value :recovery))

;; --- Recovery Request Form

(def schema
  {:password [us/required us/string]})

(defn- form-render
  [own token]
  (let [form (rum/react form-data)
        errors (rum/react form-errors)
        valid? (us/valid? form schema)]
    (letfn [(on-change [field event]
              (let [value (dom/event->value event)]
                (rs/emit! (set-value! field value))))
            (on-submit [event]
              (dom/prevent-default event)
              (rs/emit! (uda/recovery (assoc form :token token))))]
      (html
       [:form {:on-submit on-submit}
        [:div.login-content

         [:input.input-text
          {:name "password"
           :value (:password form "")
           :on-change (partial on-change :password)
           :placeholder "Password"
           :type "password"}]
         (forms/input-error errors :password)

         [:input.btn-primary
          {:name "login"
           :class (when-not valid? "btn-disabled")
           :disabled (not valid?)
           :value "Recover password"
           :type "submit"}]
         [:div.login-links
          [:a {:on-click #(rt/go :auth/login)} "Go back!"]]]]))))

(def form
  (mx/component
   {:render form-render
    :name "form"
    :mixins [mx/static rum/reactive]}))

;; --- Recovery Request Page

(defn- recovery-page-will-mount
  [own]
  (let [[token] (:rum/props own)]
    (rs/emit! (uda/validate-recovery-token token))
    own))

(defn- recovery-page-render
  [own token]
  (html
   [:div.login
    [:div.login-body
     (uum/messages)
     [:a i/logo]
     (form token)]]))

(def recovery-page
  (mx/component
   {:render recovery-page-render
    :will-mount recovery-page-will-mount
    :name "recovery-page"
    :mixins [mx/static]}))