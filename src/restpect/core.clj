(ns restpect.core
  "Assertion functions for HTTP responses."
  (:require [clojure.test :refer [do-report]]
            [clojure.string :as str]))

(defn- pretty-fname [f]
  (-> (str f)
      (clojure.main/demunge)
      (clojure.string/split #"/")
      (last)
      (clojure.string/split #"@")
      (first)))

(defprotocol Checkable
  "Defines how an expected value should be compared against the given actual
  value."
  (check [expected actual path]
    "Compare expected and actual value and return a report map with
     :expected :actual and :message if comparison fails."))

;; TODO make :path part of the returned map?
;; and maybe remove :message, since its kinda redundant with expect
(extend-protocol Checkable

  clojure.lang.IPersistentMap
  (check [expected actual path]
    (reduce
     (fn [_ [k expected]]
       (let [actual (get actual k)
             path   (conj path k)
             result (check expected actual path)]
         (when result (reduced result))))
     nil expected))

  ;; TODO look for each element of the set in the actual collection,
  ;; regardless of the position
  ;; clojure.lang.IPersistentSet
  ;; (check [expected actual path])

  clojure.lang.IPersistentCollection
  (check [expected actual path]
    (let [expected (into {} (map-indexed vector expected))]
      (check expected actual path)))

  clojure.lang.Fn
  (check [expected actual path]
    (when-not (expected actual)
      {:actual   actual
       :expected (str "to pass function: " (pretty-fname expected))
       :message  (str actual (when path (str " in " path))
                      " does not hold true for the expected function.")}))

  java.util.regex.Pattern
  (check [expected actual path]
    (when-not (re-find expected actual)
      {:actual   actual
       :expected (str "to match regex " expected)
       :message  (str actual (when path (str " in " path))
                      " does not match " expected)}))

  java.lang.Object
  (check [expected actual path]
    (when-not (= expected actual)
      {:actual   actual
       :expected expected
       :message  (str actual (when path (str " in " path))
                      " does not equal " expected ".")}))

  nil
  (check [expected actual path]
    (when-not (nil? actual)
      {:actual   actual
       :expected nil
       :message  (str actual (when path (str " in " path)) " is not nil.")})))

;; stacktrace code taken from clojure.test
(defn- stacktrace-file-and-line
  [stacktrace]
  (if (seq stacktrace)
    (let [^StackTraceElement s (first stacktrace)]
      {:file (.getFileName s) :line (.getLineNumber s)})
    {:file nil :line nil}))

(defn- get-file-and-line []
  (stacktrace-file-and-line
   (drop-while
    #(let [cl-name (.getClassName ^StackTraceElement %)]
       (or (str/starts-with? cl-name "java.lang.")
           (str/starts-with? cl-name "clojure.test$")
           (str/starts-with? cl-name "clojure.lang.")
           (str/includes? cl-name "restpect.core$")))
    (.getStackTrace (Thread/currentThread)))))

(defn expect
  "Given a response map and a spec map, check every condition of spec is
  conformed by the response at the same path. Conditions can be concrete values,
  in which case equality is tested, or functions that the actual values should
  pass."
  [response spec]
  (if-let [result (check spec response [])]
    (do (do-report (merge {:type :fail} (get-file-and-line) result))
        (do-report {:type :response :response response}))
    (do-report {:type :pass}))
  response)

;; HELPER PREDICATES
;; use defn so they have proper names in their metadata
(defn defined? [v] (not (nil? v)))

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
