# restpect

Restpect provides a set of functions to write succint and readable
integration tests over RESTful APIs.

This test:

``` clojure
(require '[clj-http.client :as http]
         '[clojure.test :refer [deftest is]])

(deftest create-get-and-delete-user
  (let [res (http/put "example.com/api/v1/users/john"
                      {:content-type :json
                       :as :json
                       :form-params {:first-name "John"
                                     :last-name "Doe"
                                     :email "john@example.com"}})]
    (is (= 201 (:status res)))
    (is (integer? (get-in res [:body :user-id]))))

  (let [res (http/get "example.com/api/v1/users/john" {:as :json})]
    (is (= 200 (:status res)))
    (is (= "John" (get-in res [:body :first-name])))
    (is (= "Doe" (get-in res [:body :last-name])))
    (is (= "john.doe@gmail.com" (get-in res [:body :email])))
    (is (integer? (get-in res [:body :user-id]))))

  (is (= 200 (:status (http/delete "example.com/api/v1/users/john"))))

  (let [res (http/get "example.com/api/v1/users/john" {:as :json
                                                       :throw-exceptions false
                                                       :coerce :always})]
    (is (= 404 (:status res)))
    (is (re-matches #"not found" (get-in res [:body :message])))))
```

Can be rewritten with restpect like:

``` clojure
(require '[restpect.core :refer [expect]]
         '[restpect.json :refer [GET PUT DELETE]]
         '[clojure.test :refer [deftest]])

(deftest create-get-and-delete-user
  (expect (PUT "example.com/api/v1/users/john" {:first-name "John"
                                                :last-name "Doe"
                                                :email "john@example.com"})
          {:status 201
           :body {:user-id integer?}})
  (expect (GET "example.com/api/v1/users/john")
          {:status 200
           :body {:first-name "John"
                  :last-name "Doe"
                  :email "john@example.com"
                  :user-id integer?}})
  (expect (DELETE "example.com/api/v1/users/john") {:status 200})
  (expect (GET "example.com/api/v1/users/john")
          {:status 404
           :body {:message #"not found"}}))
```

Or, using status shorthands:

``` clojure
(require '[restpect.core :refer [ok created not-found]]
         '[restpect.json :refer [GET PUT DELETE]]
         '[clojure.test :refer [deftest]])

(deftest create-get-and-delete-user
  (created (PUT "example.com/api/v1/users/john" {:first-name "John"
                                                 :last-name "Doe"
                                                 :email "john@example.com"})
           {:user-id integer?})
  (ok (GET "example.com/api/v1/users/john")
      {:first-name "John"
       :last-name "Doe"
       :email "john@example.com"
       :user-id integer?})
  (ok (DELETE "example.com/api/v1/users/john"))
  (not-found (GET "example.com/api/v1/users/john")
             {:message #"not found"}))
```

## Installation

Add the following to your project map as a dependency:

```clojure
[restpect "0.1.0"]
```

## Reference
### Request helpers

### Assertion functions

### Test reporter
