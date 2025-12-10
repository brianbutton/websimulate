(ns websimulate.core-test
  (:require
   [clojure.string :as str]
   [clojure.test :refer [deftest testing is are]]
   [websimulate.core :as sut]))

;; Access private vars for testing
(def update-timeouts #'sut/update-timeouts)
(def run-step #'sut/run-step)
(def try-with-error-handling #'sut/try-with-error-handling)
(def build-step-map #'sut/build-step-map)

;; =============================================================================
;; update-timeouts tests
;; =============================================================================

(deftest update-timeouts-test
  (testing "returns opts unchanged when no timeout and no delay"
    (binding [sut/*delay* 0
              sut/*delay-override* nil]
      (is (= {:foo "bar"} (update-timeouts {:foo "bar"})))))

  (testing "adds delay to existing timeout"
    (binding [sut/*delay* 5
              sut/*delay-override* nil]
      (is (= {:timeout 15} (update-timeouts {:timeout 10})))))

  (testing "delay-override replaces timeout completely"
    (binding [sut/*delay* 5
              sut/*delay-override* 100]
      (is (= {:timeout 100} (update-timeouts {:timeout 10})))))

  (testing "delay-override works even without existing timeout"
    (binding [sut/*delay* 5
              sut/*delay-override* 100]
      (is (= {:timeout 100 :foo "bar"} (update-timeouts {:foo "bar"})))))

  (testing "preserves other options when modifying timeout"
    (binding [sut/*delay* 3
              sut/*delay-override* nil]
      (is (= {:timeout 13 :other "value"}
             (update-timeouts {:timeout 10 :other "value"}))))))

;; =============================================================================
;; run-step tests
;; =============================================================================

(deftest run-step-test
  (testing "calls correct step function with single value"
    (let [called (atom nil)
          mock-step-map {:test-step (fn [driver val] (reset! called {:driver driver :val val}))}]
      (run-step :mock-driver {:test-step "hello"} mock-step-map)
      (is (= {:driver :mock-driver :val "hello"} @called))))

  (testing "calls correct step function with collection value using apply"
    (let [called (atom nil)
          mock-step-map {:test-step (fn [driver a b c] (reset! called {:driver driver :args [a b c]}))}]
      (run-step :mock-driver {:test-step ["a" "b" "c"]} mock-step-map)
      (is (= {:driver :mock-driver :args ["a" "b" "c"]} @called))))

  (testing "prints step name when debug is enabled"
    (binding [sut/*debug* true]
      (let [output (with-out-str
                     (run-step :mock-driver {:my-step "val"} {:my-step (fn [_ _])}))]
        (is (str/includes? output "running :my-step")))))

  (testing "does not print when debug is disabled"
    (binding [sut/*debug* false]
      (let [output (with-out-str
                     (run-step :mock-driver {:my-step "val"} {:my-step (fn [_ _])}))]
        (is (= "" output)))))

  (testing "prints warning for unknown step"
    (let [output (with-out-str
                   (run-step :mock-driver {:unknown-step "val"} {}))]
      (is (str/includes? output "Unknown step")))))

;; =============================================================================
;; try-with-error-handling tests
;; =============================================================================

(deftest try-with-error-handling-test
  (testing "returns function result on success"
    (is (= {:success true :msg "scenario completed"}
           (try-with-error-handling :my-scenario (fn [] {:success true :msg "scenario completed"})))))

  (testing "catches exception and returns error map with scenario name"
    (let [result (try-with-error-handling :failing-scenario (fn [] (throw (Exception. "browser error"))))]
      (is (false? (:success result)))
      (is (str/includes? (:msg result) ":failing-scenario"))
      (is (str/includes? (:msg result) "browser error")))))

;; =============================================================================
;; default-steps tests
;; =============================================================================

(deftest default-steps-test
  (testing "contains expected step types"
    (are [step-key] (contains? sut/default-steps step-key)
      :go
      :wait-visible
      :fill
      :wait-has-text-everywhere
      :wait
      :query
      :click
      :get-element-text
      :screenshot
      :println))

  (testing "default-steps does not include :conditional (added dynamically)"
    (is (not (contains? sut/default-steps :conditional)))))

;; =============================================================================
;; build-step-map tests
;; =============================================================================

(deftest build-step-map-test
  (testing "includes all default steps"
    (let [step-map (build-step-map {})]
      (are [step-key] (contains? step-map step-key)
        :go
        :wait-visible
        :fill
        :click
        :screenshot)))

  (testing "adds :conditional step"
    (let [step-map (build-step-map {})]
      (is (contains? step-map :conditional))
      (is (fn? (:conditional step-map)))))

  (testing "merges custom steps"
    (let [custom-fn (fn [_driver _] :custom-result)
          step-map (build-step-map {:my-custom-step custom-fn})]
      (is (contains? step-map :my-custom-step))
      (is (= custom-fn (:my-custom-step step-map)))))

  (testing "custom steps override defaults"
    (let [custom-go (fn [_driver _url] :custom-go)
          step-map (build-step-map {:go custom-go})]
      (is (= custom-go (:go step-map))))))

;; =============================================================================
;; run-web-simulation options binding tests
;; =============================================================================

(deftest run-web-simulation-options-test
  (testing "default options are applied"
    ;; We test this by checking the dynamic vars are bound correctly
    ;; Using a custom step that captures the bound values
    (let [captured (atom nil)]
      (with-redefs [sut/run-scenario (fn [_ _ _]
                                       (reset! captured {:debug sut/*debug*
                                                         :screenshot-folder sut/*screenshot-folder*
                                                         :delay sut/*delay*
                                                         :delay-override sut/*delay-override*
                                                         :close-driver? sut/*close-driver?*})
                                       {:success true :msg "done"})]
        (sut/run-web-simulation {:scenario-1 []})
        (is (= {:debug false
                :screenshot-folder "screenshots/"
                :delay 0
                :delay-override nil
                :close-driver? true}
               @captured)))))

  (testing "custom options are applied"
    (let [captured (atom nil)]
      (with-redefs [sut/run-scenario (fn [_ _ _]
                                       (reset! captured {:debug sut/*debug*
                                                         :screenshot-folder sut/*screenshot-folder*
                                                         :delay sut/*delay*
                                                         :delay-override sut/*delay-override*
                                                         :close-driver? sut/*close-driver?*})
                                       {:success true :msg "done"})]
        (sut/run-web-simulation {:scenario-1 []}
                                {:debug true
                                 :screenshot-folder "custom-screenshots/"
                                 :delay 5
                                 :delay-override 10
                                 :close-driver? false})
        (is (= {:debug true
                :screenshot-folder "custom-screenshots/"
                :delay 5
                :delay-override 10
                :close-driver? false}
               @captured))))))

(deftest run-web-simulation-returns-results-map-test
  (testing "returns map with scenario name keys and result values"
    (with-redefs [sut/run-scenario (fn [_ scenario-name _]
                                     {:success true :msg (str scenario-name " completed")})]
      (let [result (sut/run-web-simulation {:scenario-a [] :scenario-b []})]
        (is (map? result))
        (is (contains? result :scenario-a))
        (is (contains? result :scenario-b))
        (is (true? (get-in result [:scenario-a :success])))
        (is (true? (get-in result [:scenario-b :success])))))))

(deftest run-web-simulation-handles-errors-test
  (testing "captures errors in result map"
    (with-redefs [sut/run-scenario (fn [_ _ _]
                                     (throw (Exception. "Browser crashed")))]
      (let [result (sut/run-web-simulation {:failing-scenario []})]
        (is (false? (get-in result [:failing-scenario :success])))
        (is (str/includes? (get-in result [:failing-scenario :msg]) "Browser crashed"))))))

;; =============================================================================
;; custom-steps integration tests
;; =============================================================================

(deftest run-web-simulation-custom-steps-test
  (testing "custom steps are available in scenarios"
    (let [custom-step (fn [_driver _] :custom-result)]
      (with-redefs [sut/run-scenario (fn [_scenarios _scenario-name step-map]
                                       ;; Verify custom step is in the step map
                                       (is (contains? step-map :my-custom))
                                       (is (= custom-step (:my-custom step-map)))
                                       {:success true :msg "done"})]
        (sut/run-web-simulation {:scenario-1 [{:my-custom nil}]}
                                {:custom-steps {:my-custom custom-step}})))))
