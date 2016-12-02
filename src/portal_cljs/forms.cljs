(ns portal-cljs.forms
  (:require [portal-cljs.xhr :refer [retrieve-url xhrio-wrapper
                                     process-json-response]]
            [portal-cljs.utils :refer [base-url]]
            [portal-cljs.datastore :as datastore]
            [cljs.core.async :refer [put!]]
            [reagent.core :as r]))

(defn entity-save
  "Attempt to update entity on url using method. Call on-success or on-error
  on the resulting type of response. retrieving? is an atom containing a 
  boolean"
  [entity url method retrieving? on-success on-error]
  ;; we are retrieving
  (reset! retrieving? true)
  ;; send response to server
  (retrieve-url
   url
   method
   (js/JSON.stringify
    (clj->js entity))
   (partial
    xhrio-wrapper
    (fn [r]
      (let [response (js->clj r
                              :keywordize-keys
                              true)]
        (when (:success response)
          (on-success response))
        (when-not (:success response)
          (on-error response)))))))

(defn retrieve-entity
  "Given an entity url and id, retrieve the entity by id and process the
  response with process. process is a fn that takes as a single argument the
  response converted to a clojure data structure."
  [url process]
  (retrieve-url
   url
   "GET"
   {}
   (partial xhrio-wrapper #(process (js->clj % :keywordize-keys true)))))

(defn edit-on-success
  "Return a fn that handles the response from a successful edit of an existing 
  entity. entity-type is a str, edit-entity,current-entity are atoms, 
  alert-success is an atom containing a string. aux-fn is an optional
  auxiliary fn that is executed on success. 

  example usage:
  (edit-on-success \"user\" edit-user current-user alert-success)
  (edit-on-success \"coupon\" edit-coupon current-coupon alert-success)
  "
  [{:keys [entity-type entity-get-url-fn edit-entity current-entity
           alert-success aux-fn channel-topic]
    :or {aux-fn (fn [] true)
         channel-topic (str entity-type "s")
         entity-get-url-fn (fn [id] (str base-url entity-type "/" id))}}]
  (fn [response]
    (retrieve-url
     (entity-get-url-fn (:id response))
     "GET"
     {}
     (process-json-response
      (fn [res]
        (put! datastore/modify-data-chan
              {:topic channel-topic
               :data [res]})
        (reset! edit-entity (assoc  res
                                    :errors nil
                                    :retrieving? false))
        (reset! alert-success "Successfully updated!")
        (reset! current-entity
                (first res))
        (aux-fn))))))

(defn edit-on-error
  "Return a fn that handles the response from unsuccessful edit of an existing 
  entity. Assumes that there will be errors in the response. edit-entity is an
  atom. aux-fn is an optional auxiliary fn that is executed on success. 

  example usage:

  (edit-on-error edit-user)
  (edit-on-error edit-coupon)
  "
  [edit-entity & {:keys [aux-fn response-fn]
                  :or {aux-fn (fn [] true)
                       response-fn (fn [response] true)}}]
  (fn [response]
    (reset! (r/cursor edit-entity [:retrieving?]) false)
    (reset! (r/cursor edit-entity [:alert-success]) "")
    (reset! (r/cursor edit-entity [:errors]) (first (:validation response)))
    (aux-fn)
    (response-fn (first (:validation response)))))
