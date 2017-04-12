(ns restpect.json
  "Simple wrappers around clj-http with sane defaults for JSON API testing."
  (:require [clj-http.client :as http]
            [clj-http.conn-mgr :refer [make-reusable-conn-manager]]))

;; not sure if this helps
(def cm (make-reusable-conn-manager {:threads 4 :default-per-route 4}))

(defn wrap-json
  ([method] (wrap-json method :form-params))
  ([method body-kw]
   (fn request
     ([url] (request url {} {}))
     ([url body] (request url body {}))
     ([url body extras]
      (-> (merge {:method method
                  :url url
                  body-kw body
                  :content-type :json
                  :as :json-strict
                  :coerce :always
                  :throw-exceptions false
                  :connection-manager cm} extras)
          (http/request)
          (assoc :request {:url url :method method}))))))

(def POST (wrap-json :post))
(def PUT (wrap-json :put))
(def PATCH (wrap-json :patch))
(def DELETE (wrap-json :delete))
(def GET (wrap-json :get :query-params))
(def HEAD (wrap-json :head :query-params))
(def OPTIONS (wrap-json :options :query-params))
