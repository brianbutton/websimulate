(ns websimulate.core
  (:require
   [etaoin.api :as e]
   [clojure.repl :as repl]))

(def global-debug false)

(defn run-step [driver step step-function-map]
  (when global-debug
    (println (str "running " (first (keys step)))))
  (let [key (first (keys step))
        value (first (vals step))]
    (if-let [fn (get step-function-map key)]
      (cond
        (coll? value) (apply fn driver value)
        :else (fn driver value))
      (println "Unknown step" step))))

(def global-delay 0)

(def step-function-map
  {:go e/go
   :wait-visible e/wait-visible
   :fill (fn [driver & args] (apply e/fill driver args))
   :wait-has-text-everywhere e/wait-has-text-everywhere
   :wait (fn [driver delay] (e/wait driver (+ global-delay delay)))
   :query e/query
   :click e/click
   :screenshot e/screenshot
   :println println
   :conditional
   (fn [driver [step & args] then-steps else-steps]
     (if (try
           (apply (get step-function-map step) driver args)
           (catch Exception e))
       (doseq [sub-step then-steps]
         (run-step driver sub-step step-function-map))
       (doseq [sub-step else-steps]
         (run-step driver sub-step step-function-map))))})

(defn run-test [test-data test-name step-function-map]
  (println (str "Starting " test-name "..."))
  (let [driver (e/chrome)]
    (try
      (doseq [step (get test-data test-name)]
        (run-step driver step step-function-map))
      (finally
        (e/quit driver)))
    {:success true :msg (str test-name " completed successfully")}))

(defn try-with-error-handling [fn]
  (let [fn-name (repl/demunge (str fn))]
    (try
      (fn)
      (catch Exception e
        {:success false
         :msg (str "Error in " fn-name ": " (.getMessage e))}))))

(comment
  (def test-data
    {:checkout-test
     [{:go "https://www.google.com/"}
      {:wait 1}
      {:screenshot "screenshots/google.png"}]})

  (->>
    test-data
    (pmap
      (fn [[k v]]
        [k
         (try-with-error-handling
           #(run-test test-data k step-function-map))]))
    (doall)
    (into {})))
