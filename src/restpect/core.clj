(ns restpect.core
  "Assertion functions for HTTP responses."
  (:require [clojure.test :refer [do-report]]
            [clojure.string :as str]))

;; yanked some clojure.test code to properly report failure file and line
(defn- get-fail-report
  "build a report map, patching file/line logic to exclude this file"
  [actual expected msg]
  (let [get-file-and-line #'clojure.test/stacktrace-file-and-line
        file-line (get-file-and-line (drop-while
                                      #(let [cl-name (.getClassName ^StackTraceElement %)]
                                         (or (str/starts-with? cl-name "java.lang.")
                                             (str/starts-with? cl-name "clojure.test$")
                                             (str/starts-with? cl-name "clojure.lang.")
                                             (str/includes? cl-name "restpect.core$")))
                                      (.getStackTrace (Thread/currentThread))))]
    (merge file-line {:type :fail
                      :actual actual
                      :expected expected
                      :message msg})))

(defn- as-vec
  "If value isn't a collection, wrap it in a vector."
  [v] (if (coll? v) v [v]))

(defn- as-map
  "Turn non map collections into {index element} maps."
  [v]
  (if (map? v) v  (into {} (map-indexed vector v))))

(defn- flatten-map
  "Reduce m to a flat map of path/values:
    {:body [{:message :hi}]} -> {[:body 0 :message] :hi}"
  [m]
  (if (coll? m)
    (reduce (fn [new-map [k v]]
              (if (coll? v)
                (flatten-map (into new-map (map (fn [[k2 v2]]
                                                  [(conj (as-vec k) k2) v2])
                                                (as-map v))))
                (assoc new-map (as-vec k) v)))
            {} (as-map m))
    m))

(defprotocol Expectable
  "Defines how an expected value should be compared against the given actual
  value."
  (compare-and-report [expected actual path]
    "Compare expected and actual value and return a report map if comparison
     fails."))

(extend-protocol Expectable

  clojure.lang.IPersistentCollection
  (compare-and-report [expected actual path]
    (loop [spec (seq (flatten-map expected))]
      (if-let [[[path expected] & tail] spec]
        (let [actual (get-in actual path)]
          (or (compare-and-report expected actual path) (recur tail))))))

  clojure.lang.Fn
  (compare-and-report [expected actual path]
    (when-not (expected actual)
      ;; TODO improve this message with readable function
      (get-fail-report actual "to pass function"
                       (str actual (when path (str " in " path))
                            " does not hold true for the expected function."))))

  java.util.regex.Pattern
  (compare-and-report [expected actual path]
    (when-not (re-matches expected actual)
      (get-fail-report actual (str "to match regex " expected)
                       (str actual (when path (str " in " path))
                            " does not match " expected))))

  java.lang.Object
  (compare-and-report
    [expected actual path]
    (when-not (= expected actual)
      (get-fail-report actual expected
                       (str actual (when path (str " in " path)) " does not equal " expected ".")))))

(defn expect
  "Given a response map and a spec map, check every condition of spec is
  conformed by the response at the same path. Conditions can be concrete values,
  in which case equality is tested, or functions that the actual values should
  pass."
  [response spec]
  (if-let [result (compare-and-report spec response nil)]
    (do (do-report result)
        (do-report {:type :response :response response}))
    (do-report {:type :pass}))
  response)

;; HELPER PREDICATES
(def defined? (comp not nil?))

(defn has-keys [keys] #(every? % keys))
(defn one-of [values] #((set values) %))

;; STATUS SHORTHANDS
(defn- status-shorthand [status]
  (fn
    ([r] (expect r {:status status}))
    ([r body] (expect r {:status status :body body}))))

(def continue (status-shorthand 100))
(def switching-protocols (status-shorthand 101))

(def ok (status-shorthand 200))
(def success ok)
(def created (status-shorthand 201))
(def accepted (status-shorthand 202))
(def non-authoritative (status-shorthand 203))
(def no-content (status-shorthand 204))
(def reset-content (status-shorthand 205))
(def partial-content (status-shorthand 206))

(def multiple-choices (status-shorthand 300))
(def moved (status-shorthand 301))
(def found (status-shorthand 302))
(def see-other (status-shorthand 303))
(def not-modified (status-shorthand 304))
(def use-proxy (status-shorthand 305))

(def bad-request (status-shorthand 400))
(def unauthorized (status-shorthand 401))
(def payment-required (status-shorthand 402))
(def forbidden (status-shorthand 403))
(def not-found (status-shorthand 404))
(def method-not-allowed (status-shorthand 405))
(def not-acceptable (status-shorthand 406))
(def proxy-auth-required (status-shorthand 407))
(def request-timeout (status-shorthand 408))
(def conflict (status-shorthand 409))
(def gone (status-shorthand 410))
(def lenght-required (status-shorthand 411))
(def precondition-failed (status-shorthand 412))
(def entity-too-large (status-shorthand 413))
(def uri-too-long (status-shorthand 414))
(def unsupported-media (status-shorthand 415))
(def range-not-satisfiable (status-shorthand 416))
(def expectation-failed (status-shorthand 417))
(def im-a-teapot (status-shorthand 418))

(def internal-error (status-shorthand 500))
(def not-implemented (status-shorthand 501))
(def bad-gateway (status-shorthand 502))
(def unavailable (status-shorthand 503))
(def gateway-timeout (status-shorthand 504))
(def version-not-supported (status-shorthand 505))
