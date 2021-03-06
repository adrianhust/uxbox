;; This Source Code Form is subject to the terms of the Mozilla Public
;; License, v. 2.0. If a copy of the MPL was not distributed with this
;; file, You can obtain one at http://mozilla.org/MPL/2.0/.
;;
;; This Source Code Form is "Incompatible With Secondary Licenses", as
;; defined by the Mozilla Public License, v. 2.0.
;;
;; Copyright (c) 2020 UXBOX Labs SL

(ns uxbox.main.ui.workspace.sidebar.options.measures
  (:require
   [rumext.alpha :as mf]
   [uxbox.builtins.icons :as i]
   [uxbox.main.store :as st]
   [uxbox.main.refs :as refs]
   [uxbox.common.data :as d]
   [uxbox.util.dom :as dom]
   [uxbox.main.data.workspace :as udw]
   [uxbox.util.math :as math]
   [uxbox.util.i18n :refer [t] :as i18n]))

(mf/defc measures-menu
  [{:keys [shape options] :as props}]
  (let [options (or options #{:size :position :rotation :radius})
        locale (i18n/use-locale)

        data (deref refs/workspace-data)
        parent (get-in data [:objects (:frame-id shape)])

        x (cond
            (:x shape) :x
            (:cx shape) :cx)

        y (cond
            (:y shape) :y
            (:cy shape) :cy)

        on-size-change
        (fn [event attr]
          (let [value (-> (dom/get-target event)
                          (dom/get-value)
                          (d/parse-integer 0))]
            (st/emit! (udw/update-rect-dimensions (:id shape) attr value))))

        on-circle-size-change
        (fn [event attr]
          (let [value (-> (dom/get-target event)
                          (dom/get-value)
                          (d/parse-integer 0)
                          (/ 2))] ; Convert back to radius before update
            (st/emit! (udw/update-circle-dimensions (:id shape) attr value))))

        on-proportion-lock-change
        (fn [event]
          (st/emit! (udw/toggle-shape-proportion-lock (:id shape))))

        on-position-change
        (fn [event attr]
          (let [value (-> (dom/get-target event)
                          (dom/get-value)
                          (d/parse-integer 0)
                          (+ (attr parent)))] ; Convert back to absolute position before update
            (st/emit! (udw/update-position (:id shape) {attr value}))))

        on-rotation-change
        (fn [event]
          (let [value (-> (dom/get-target event)
                          (dom/get-value)
                          (d/parse-integer 0))]
            (st/emit! (udw/update-shape (:id shape) {:rotation value}))))

        on-radius-change
        (fn [event]
          (let [value (-> (dom/get-target event)
                          (dom/get-value)
                          (d/parse-integer 0))]
            (st/emit! (udw/update-shape (:id shape) {:rx value :ry value}))))

        on-width-change #(on-size-change % :width)
        on-height-change #(on-size-change % :height)
        on-pos-x-change #(on-position-change % :x)
        on-pos-y-change #(on-position-change % :y)
        on-size-rx-change #(on-circle-size-change % :rx)
        on-size-ry-change #(on-circle-size-change % :ry)]

    [:div.element-set
     [:div.element-set-content

      ;; WIDTH & HEIGHT
      (when (options :size)
       [:div.row-flex
        [:span.element-set-subtitle (t locale "workspace.options.size")]
        [:div.lock-size {:class (when (:proportion-lock shape) "selected")
                         :on-click on-proportion-lock-change}
         (if (:proportion-lock shape)
           i/lock
           i/unlock)]
        [:div.input-element.pixels
         [:input.input-text {:type "number"
                             :min "0"
                             :no-validate true
                             :on-change on-width-change
                             :value (str (-> (:width shape)
                                             (d/coalesce 0)
                                             (math/round)))}]]


        [:div.input-element.pixels
         [:input.input-text {:type "number"
                             :min "0"
                             :no-validate true
                             :on-change on-height-change
                             :value (str (-> (:height shape)
                                             (d/coalesce 0)
                                             (math/round)))}]]])

      ;; Circle RX RY
      (when (options :circle-size)
        [:div.row-flex
         [:span.element-set-subtitle (t locale "workspace.options.size")]
         [:div.lock-size {:class (when (:proportion-lock shape) "selected")
                          :on-click on-proportion-lock-change}
          (if (:proportion-lock shape)
            i/lock
            i/unlock)]
         [:div.input-element.pixels
          [:input.input-text {:type "number"
                              :min "0"
                              :on-change on-size-rx-change
                              :value (str (-> (* 2 (:rx shape)) ; Show to user diameter and not radius
                                              (d/coalesce 0)
                                              (math/round)))}]]
         [:div.input-element.pixels
          [:input.input-text {:type "number"
                              :min "0"
                              :on-change on-size-ry-change
                              :value (str (-> (* 2 (:ry shape))
                                              (d/coalesce 0)
                                              (math/round)))}]]])

      ;; POSITION
      (when (options :position)
        [:div.row-flex
        [:span.element-set-subtitle (t locale "workspace.options.position")]
        [:div.input-element.pixels
         [:input.input-text {:placeholder "x"
                             :type "number"
                             :no-validate true
                             :on-change on-pos-x-change
                             :value (str (-> (- (x shape) (:x parent)) ; Show to user position relative to frame
                                             (d/coalesce 0)
                                             (math/round)))}]]
        [:div.input-element.pixels
         [:input.input-text {:placeholder "y"
                             :type "number"
                             :no-validate true
                             :on-change on-pos-y-change
                             :value (str (-> (- (y shape) (:y parent))
                                             (d/coalesce 0)
                                             (math/round)))}]]])

      (when (options :rotation)
       [:div.row-flex
        [:span.element-set-subtitle (t locale "workspace.options.rotation")]
        [:div.input-element.degrees
         [:input.input-text
          {:placeholder ""
           :type "number"
           :no-validate true
           :min "0"
           :max "360"
           :on-change on-rotation-change
           :value (str (-> (:rotation shape)
                           (d/coalesce 0)
                           (math/round)))}]]
        [:input.slidebar
         {:type "range"
          :min "0"
          :max "360"
          :step "1"
          :no-validate true
          :on-change on-rotation-change
          :value (str (-> (:rotation shape)
                          (d/coalesce 0)))}]])

      (when (options :radius)
       [:div.row-flex
        [:span.element-set-subtitle (t locale "workspace.options.radius")]
        [:div.input-element.pixels
         [:input.input-text
          {:placeholder "rx"
           :type "number"
           :on-change on-radius-change
           :value (str (-> (:rx shape)
                           (d/coalesce 0)
                           (math/round)))}]]
        [:div.input-element]])]]))
