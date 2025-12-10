# Introduction to websimulate

websimulate is a Clojure library for declarative web browser automation. Instead of writing imperative code to control a browser, you define scenarios as data structures that describe what actions to take.

## Why websimulate?

- **Data-driven**: Scenarios are just Clojure data structures (maps and vectors)
- **Parallel execution**: Run multiple scenarios simultaneously using Java 21+ virtual threads
- **Simple API**: One function (`run-web-simulation`) does everything
- **Built on etaoin**: Leverages the battle-tested etaoin library for Chrome automation

## Core Concepts

### Scenarios

A scenario is a named sequence of steps. You define scenarios as a map:

```clojure
(def my-scenarios
  {:scenario-name [step1 step2 step3 ...]
   :another-scenario [step1 step2 ...]})
```

### Steps

Each step is a map with a single key (the step type) and value (the arguments):

```clojure
{:go "https://example.com"}           ; Navigate to URL
{:click {:id :submit-button}}         ; Click element
{:fill [{:id :email} "test@test.com"]} ; Fill form field
{:wait 2}                              ; Wait 2 seconds
{:screenshot "my-screenshot"}          ; Take screenshot
```

### Selectors

Selectors identify elements on the page. websimulate uses etaoin's selector syntax:

```clojure
{:id :my-element}                    ; By ID
{:class :my-class}                   ; By CSS class
{:tag :button}                       ; By HTML tag
{:name :email}                       ; By name attribute
{:css "div.container > button"}      ; CSS selector
{:xpath "//button[@type='submit']"}  ; XPath
{:tag :button :fn/text "Submit"}     ; By tag and text content
```

## Running Scenarios

```clojure
(require '[websimulate.core :refer [run-web-simulation]])

(def scenarios
  {:my-test
   [{:go "https://example.com"}
    {:wait-visible {:tag :h1}}
    {:screenshot "example"}]})

;; Run with defaults
(run-web-simulation scenarios)

;; Run with options
(run-web-simulation scenarios
  {:debug true                      ; Print each step
   :screenshot-folder "screenshots/" ; Where to save screenshots
   :delay 1                         ; Add 1s to all timeouts
   :close-driver? true})            ; Close browser when done
```

## Results

`run-web-simulation` returns a map of results:

```clojure
{:my-test {:success true :msg ":my-test completed successfully"}
 :other-test {:success false :msg "Error in :other-test: Element not found"}}
```

## Virtual Threads

websimulate uses Java 21+ virtual threads for parallel execution. Each scenario gets its own lightweight virtual thread, allowing you to run hundreds of browser sessions concurrently without thread pool exhaustion.

This is particularly useful for:
- Load testing web applications
- Parallel scraping of multiple pages
- Running test suites quickly

## Next Steps

- See the [README](../README.md) for detailed examples
- Check the source code docstrings for API reference
- Look at the test suite for more usage patterns
