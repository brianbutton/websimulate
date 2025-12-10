(ns websimulate.core
  "A library for declarative web browser automation and simulation.

   Define scenarios as data structures and run them in parallel.
   Built on top of etaoin for Chrome browser automation.

   Java 21+ recommended for virtual threads; falls back to cached thread pool on older versions.

   Basic usage:

     (require '[websimulate.core :refer [run-web-simulation]])

     (run-web-simulation
       {:my-scenario
        [{:go \"https://example.com\"}
         {:wait-visible {:tag :h1}}
         {:screenshot \"example\"}]})

   Custom steps:

     (run-web-simulation scenarios
       {:custom-steps {:my-step (fn [driver arg] ...)}})

   See `run-web-simulation` for the main entry point and `default-steps` for built-in steps."
  (:require
   [clojure.string :as str]
   [etaoin.api :as e])
  (:import
   (java.util.concurrent Executors)))

;; =============================================================================
;; Dynamic Configuration
;; =============================================================================

(def ^:dynamic *debug*
  "When true, prints each step name as it executes. Default: false"
  false)

(def ^:dynamic *screenshot-folder*
  "Directory path where screenshots are saved. Default: \"screenshots/\""
  "screenshots/")

(def ^:dynamic *delay*
  "Additional delay in seconds added to all timeout operations. Default: 0"
  0)

(def ^:dynamic *delay-override*
  "When set, overrides all delays/timeouts with this value. Default: nil"
  nil)

(def ^:dynamic *close-driver?*
  "When true, closes the browser after scenario completes. Default: true"
  true)

;; =============================================================================
;; Internal Implementation
;; =============================================================================

(defn- run-step
  "Executes a single step in a scenario.
   Steps are maps with a single key (the step type) and value (the arguments)."
  [driver step step-function-map]
  (when *debug*
    (println (str "running " (first (keys step)))))
  (let [step-key (first (keys step))
        step-value (first (vals step))]
    (if-let [step-fn (get step-function-map step-key)]
      (cond
        (coll? step-value) (apply step-fn driver step-value)
        :else (step-fn driver step-value))
      (println "Unknown step" step))))

(defn- update-timeouts
  "Applies delay configuration to timeout options."
  [opts]
  (cond
    *delay-override*
    (assoc opts :timeout *delay-override*)

    :else
    (if (:timeout opts)
      (assoc opts :timeout (+ *delay* (:timeout opts)))
      opts)))

(defn- make-conditional-step
  "Creates the :conditional step function with access to the full step map.
   Uses an atom reference to support nested conditionals."
  [step-map-atom]
  (fn [driver [step & args] then-steps else-steps]
    (let [step-function-map @step-map-atom]
      (if (try
            (apply (get step-function-map step) driver args)
            (catch Exception _))
        (doseq [sub-step then-steps]
          (run-step driver sub-step step-function-map))
        (doseq [sub-step else-steps]
          (run-step driver sub-step step-function-map))))))

;; =============================================================================
;; Public API - Step Functions
;; =============================================================================

(def default-steps
  "Default map of step keywords to their implementation functions.

   Use this to inspect available steps or as a base for custom step maps.
   Custom steps can be passed via the :custom-steps option to run-web-simulation.

   Available steps:
   - :go [url]                      - Navigate to URL
   - :wait [seconds]                - Wait for specified seconds
   - :wait-visible [selector opts?] - Wait for element to be visible
   - :wait-has-text-everywhere [text opts?] - Wait for text anywhere on page
   - :fill [selector text]          - Fill form field with text
   - :click [selector]              - Click an element
   - :js-click [selector]           - Click via JavaScript (bypasses overlays)
   - :scroll-query [selector]       - Scroll element into view
   - :query [selector]              - Query for element (returns element)
   - :get-element-text [selector]   - Get text content of element
   - :screenshot [filename]         - Take screenshot (saved to *screenshot-folder*)
   - :println [& args]              - Print to console
   - :conditional [[step & args] then-steps else-steps] - Conditional branching

   Custom step function signature: (fn [driver & args] ...)

   Example - adding a custom step:

     (run-web-simulation scenarios
       {:custom-steps
        {:scroll-to-bottom
         (fn [driver _]
           (etaoin.api/js-execute driver \"window.scrollTo(0, document.body.scrollHeight)\"))

         :log-url
         (fn [driver _]
           (println \"Current URL:\" (etaoin.api/get-url driver)))}})"
  {:go e/go

   :wait-visible
   (fn [driver selector & [opts]]
     (e/wait-visible driver selector (update-timeouts opts)))

   :fill
   (fn [driver & args]
     (apply e/fill driver args))

   :wait-has-text-everywhere
   (fn [driver text & [opts]]
     (e/wait-has-text-everywhere driver text (update-timeouts opts)))

   :wait
   (fn [driver delay]
     (e/wait driver (or *delay-override* (+ *delay* delay))))

   :query e/query

   :click e/click

   :js-click
   (fn [driver selector]
     ;; Click via JavaScript - bypasses overlays like sticky footers
     ;; First scroll element to top of viewport, wait, then click
     (e/scroll-query driver selector {:block :start})
     (Thread/sleep 500)
     (e/click driver selector))

   :scroll-query
   (fn [driver selector]
     (e/scroll-query driver selector))

   :get-element-text e/get-element-text

   :screenshot
   (fn [driver filename]
     (e/screenshot driver (str *screenshot-folder* filename ".png")))

   :println println})

(defn- build-step-map
  "Builds the complete step function map by merging custom steps with defaults.
   Uses an atom to allow :conditional to reference the complete map (including itself)."
  [custom-steps]
  (let [step-map-atom (atom nil)
        merged (merge default-steps custom-steps)
        final-map (assoc merged :conditional (make-conditional-step step-map-atom))]
    (reset! step-map-atom final-map)
    final-map))

(defn- run-scenario
  "Executes all steps in a scenario sequentially."
  [scenarios scenario-name step-function-map]
  (println (str "Starting " scenario-name "..."))
  (let [driver (e/chrome)]
    (try
      (doseq [step (get scenarios scenario-name)]
        (run-step driver step step-function-map))
      (finally
        (when *close-driver?*
          (e/quit driver))))
    {:success true :msg (str scenario-name " completed successfully")}))

(defn- try-with-error-handling
  "Wraps function execution with error handling, returning a result map."
  [scenario-name f]
  (try
    (f)
    (catch Exception e
      {:success false
       :msg (str "Error in " scenario-name ": " (.getMessage e))})))

(def ^:private java-version
  "Returns the major Java version as an integer."
  (-> (System/getProperty "java.version")
      (str/split #"\.")
      first
      Integer/parseInt))

(defn- create-executor
  "Creates an executor service. Uses virtual threads on Java 21+, cached thread pool otherwise."
  []
  (if (>= java-version 21)
    ;; Use reflection to call newVirtualThreadPerTaskExecutor (avoids compile-time resolution)
    (.invoke (.getMethod Executors "newVirtualThreadPerTaskExecutor" (into-array Class []))
             nil (into-array Object []))
    (Executors/newCachedThreadPool)))

(defn- pmap-virtual
  "Maps f over coll using virtual threads (Java 21+) or cached thread pool (Java < 21).
   Better than pmap for I/O-bound operations like browser automation.
   Properly conveys dynamic bindings to spawned threads."
  [f coll]
  (let [executor (create-executor)
        ;; Capture current bindings to convey to threads
        bindings (get-thread-bindings)]
    (try
      (->> coll
           (mapv #(let [item %]
                    (.submit executor
                             (fn []
                               (with-bindings bindings
                                 (f item))))))
           (mapv #(.get %)))
      (finally
        (.shutdown executor)))))

;; =============================================================================
;; Public API - Main Entry Point
;; =============================================================================

(defn run-web-simulation
  "Runs web simulation scenarios in parallel. Returns a map of scenario-name -> result.

   Each scenario runs in its own virtual thread with a dedicated Chrome browser instance.

   Arguments:
   - scenarios - A map of scenario-name (keyword) to steps (vector of step maps)
   - opts      - Optional configuration map

   Options:
   - :debug             - Print step names as they execute (default: false)
   - :screenshot-folder - Path for screenshots (default: \"screenshots/\")
   - :delay             - Additional delay in seconds added to timeouts (default: 0)
   - :delay-override    - Override all delays with this value (default: nil)
   - :close-driver?     - Close browser after scenario completes (default: true)
   - :custom-steps      - Map of custom step keywords to functions (default: nil)
                          Custom steps are merged with default-steps, allowing overrides.
                          Step function signature: (fn [driver & args] ...)

   Returns:
   A map of scenario-name -> {:success bool :msg string}

   Example:

     (run-web-simulation
       {:login-flow
        [{:go \"https://myapp.com/login\"}
         {:fill [{:id :username} \"user@example.com\"]}
         {:fill [{:id :password} \"secret\"]}
         {:click {:tag :button :fn/text \"Sign In\"}}
         {:wait-visible {:class :dashboard}}
         {:screenshot \"logged-in\"}]

        :search-test
        [{:go \"https://google.com\"}
         {:fill [{:name :q} \"clojure\"]}
         {:click {:name :btnK}}
         {:wait 2}
         {:screenshot \"search-results\"}]}

       {:debug true
        :screenshot-folder \"test-screenshots/\"})

   Example with custom steps:

     (run-web-simulation
       {:my-scenario
        [{:go \"https://example.com\"}
         {:scroll-down nil}
         {:log-current-url nil}]}

       {:custom-steps
        {:scroll-down
         (fn [driver _]
           (etaoin.api/js-execute driver \"window.scrollBy(0, 500)\"))

         :log-current-url
         (fn [driver _]
           (println \"URL:\" (etaoin.api/get-url driver)))}})

   Java 21+ recommended for virtual threads; falls back to cached thread pool on older versions."
  ([scenarios] (run-web-simulation scenarios {}))
  ([scenarios opts]
   (let [step-function-map (build-step-map (get opts :custom-steps {}))]
     (binding [*debug* (get opts :debug false)
               *screenshot-folder* (get opts :screenshot-folder "screenshots/")
               *delay* (get opts :delay 0)
               *delay-override* (get opts :delay-override nil)
               *close-driver?* (get opts :close-driver? true)]
       (->>
        scenarios
        (pmap-virtual
         (fn [[k _]]
           [k
            (try-with-error-handling k
                                     #(run-scenario scenarios k step-function-map))]))
        (into {}))))))
