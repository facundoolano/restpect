# restpect

Restpect provides a set of functions to write succint and readable
integration tests over RESTful APIs.

This test:

```clojure
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
    (is (re-matches #".+ not found" (get-in res [:body :message])))))
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
           :body {:message #".+ not found"}}))
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
             {:message #".+ not found"}))
```

## Installation

Add the following to your project map as a dependency:

```clojure
[restpect "0.1.0"]
```

## Reference
### Request helpers
The `restpect.json` namespace provides wrappers around the [clj-http](https://github.com/dakrone/clj-http)
request functions with sane defaults for JSON API requests (coerce request and
response as JSON, don't throw exceptions on 4xx and 5xx responses, etc.).

All these functions have the following signature:

``` clojure
(POST url)
(POST url body)
(POST url body extras)
```

The `body` is passed to clj-http as the `:form-params` for `POST`, `PUT`, `PATCH`
and `DELETE`, and as the `:query-params` for `GET`, `HEAD` and `OPTIONS`.

`extras` is a map of overrides passed to the clj-http call.

### Assertion functions

#### expect
The main assertion function is `restpect.core/expect`:

``` clojure
(expect response spec)
```

The first argument (usually a clj-http response map, although it can be
any value), will be compared against the given spec with the following criteria:

* For maps, compare the value of each key in the spec with the value at the same key
of the response, using `expect` recursively.
* For other collections, compare each element in the spec with the same element at the
same position in the response, using `expect` recursively.
* For functions, pass the value in the response to the spec function expecting a
truthy result.
* For Regular expressions match the spec with the actual value.
* For the rest of the values, expect the spec and the response values to be equal.

Example:

``` clojure
(expect (GET url)
    {:status 404
     :body [{:result nil?
             :code 125
             :message #".+ not found"}]})
```

This assertion is equivalent to the following:

``` clojure
(let [res (GET url)]
  (is (= 404 (:status res)))
  (is (nil? (get-in res [:body 0 :result])))
  (is (= 125 (get-in res [:body 0 :code])))
  (is (re-matches #".+ not found" (get-in res [:body 0 :message]))))
```

As seen in the example, `expect` is opinionated in the sense that it makes it
simple to test for values and conditions on specific fields of the reponses
rather than doing an exact comparison of the payloads.

#### Status shorthands
`restpect.core` also provides a set of wrappers around `expect` with the
names of the different HTTP response status codes: `ok`, `created`, `bad-request`,
`not-found`, etc.

These helpers implicitly validate the `:status` value of the given response map,
and can optionally take a second argument that will be compared against the
response body.

Using status shorthands, the example from the previous section becomes:

``` clojure
(not-found (GET url)
    [{:result nil?
      :code 125
      :message #".+ not found"}])
```

### Test reporter

Restpect also provides a custom test reporter that adds request and response
information to failure messages (provided by `expect`) and does some formatting:

![example report](report.png)

The report multimethod can be found in `restpect.report/report` and can be used
with plugins that allow to override the test reporter, like 
[eftest](https://github.com/weavejester/eftest)
and [lein-test-refresh](https://github.com/jakemcc/lein-test-refresh):

``` clojure
;; project.clj
:eftest {:report restpect.report/report}
:test-refresh {:report restpect.report/report}
```

If you already work with a custom reporter and just want to add some
request/reponse data to its output, consider adding a defmethod for
`:type :response` as seen [here](https://github.com/facundoolano/restpect/blob/master/src/restpect/report.clj#L89).
