(ns restpect.report
  "Customize test reporter to add useful http context on failures."
  (:require [clojure.test :refer [do-report testing-contexts-str
                                  testing-vars-str *testing-contexts*
                                  with-test-out inc-report-counter
                                  *report-counters*]]
            [clojure.string :as str]
            [puget.color.ansi :as ansi]
            [ultra.test.diff :as ultra]))

(defn request-str [m]
  (-> (get-in m [:request :method])
      (name)
      (clojure.string/upper-case)
      (str " " (get-in m [:request :url]))))

(defn print-response
  [res]
  (when res
    (println " request:" (request-str res))
    (println "  status:" (pr-str (:status res)))
    (println "    body:" (ultra/pretty (:body res))))
  (println))

(defn- pretty-test-name [v]
  (-> (:name (meta v))
      (clojure.string/replace #"-" " ")
      (clojure.string/capitalize)))

;; Copied the clojure.test/report definitions for most types
(defmulti report :type)

(defmethod report :default [m]
  (with-test-out (prn m)))

(defmethod report :pass [m]
  (with-test-out (inc-report-counter :pass)))

(defmethod report :summary [m]
  (with-test-out
    (println "\nRan" (:test m) "tests containing"
             (+ (:pass m) (:fail m) (:error m)) "assertions.")
    (println (:fail m) "failures," (:error m) "errors.")))

(defmethod report :begin-test-ns [m]
  (with-test-out
    (println "\nTesting" (ns-name (:ns m)))))

(defmethod report :end-test-ns [m])

;; Custom definitions

(defmethod report :fail [m]
  (with-test-out
    (inc-report-counter :fail)
    (println (str "    " (ansi/sgr (str "✗ " (testing-vars-str m)) :red)))
    (when (seq *testing-contexts*) (println (testing-contexts-str)))
    (ultra/print-expected (:actual m) (:expected m))
    (when-let [message (:message m)]
      (println " message:" message))))

;; TODO improve this one?
(require '[clojure.stacktrace :as stack])
(defmethod report :error [m]
  (with-test-out
    (inc-report-counter :error)
    (println (str "    " (ansi/sgr (str "! " (testing-vars-str m)) :red)))
    (when (seq *testing-contexts*) (println (testing-contexts-str)))
    (println)
    (when-let [message (:message m)]
      (println " message:" message))
    (println "expected:" (pr-str (:expected m)))
    (print "  actual: ")
    (let [actual (:actual m)]
      (if (instance? Throwable actual)
        (stack/print-cause-trace actual clojure.test/*stack-trace-depth*)
        (prn actual))
      (println))))

(def ^:dynamic *before-counters* (ref nil))

(defmethod report :begin-test-var [m]
  (dosync (ref-set *before-counters* @*report-counters*)))

(defmethod report :end-test-var [m]
  (let [before (select-keys @*before-counters* [:pass :fail :error])
        after (select-keys @*report-counters* [:pass :fail :error])
        empty-test? (= before after)
        passing-test? (= (dissoc before :pass) (dissoc after :pass))]
    (cond empty-test?
          (println (ansi/sgr (str "    - " (pretty-test-name (:var m))) :cyan))

          passing-test?
          (println (str "    " (ansi/sgr "✓ " :green) (pretty-test-name (:var m)))))))

(defmethod report :response [m]
  (print-response (:response m)))
