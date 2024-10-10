# websimulate

websimulate is a Clojure library designed to simulate web interactions and run automated tests using the Etaoin WebDriver library. It provides a flexible framework for defining and executing web-based test scenarios.

## Features

- Easy-to-define test scenarios using a map-based structure
- Built-in support for common web interactions (navigation, clicking, filling forms, etc.)
- Conditional steps for more complex test flows
- Screenshot capture capability
- Extensible step function map for custom actions

## Getting Started

### Prerequisites

- Java JDK (version 8 or higher)
- Leiningen (Clojure build tool)
- Chrome browser (for running tests with ChromeDriver)

### Installation

1. Clone the repository:
   ```
   git clone https://github.com/your-username/websimulate.git
   cd websimulate
   ```

2. Install dependencies:
   ```
   lein deps
   ```

### Usage

1. Define your test scenarios in the `test-data` map within the `websimulate.core` namespace. For example:

```clojure
(def test-data
  {:checkout-test
   [{:go "https://www.example.com/"}
    {:wait 1}
    {:fill {:q "#search-input"} "test query"}
    {:click {:q "#search-button"}}
    {:wait-visible {:q "#results"}}
    {:screenshot "screenshots/search-results.png"}]})
```


2. Run your tests using the `run-test` function:

```clojure
(run-test test-data :checkout-test step-function-map)
```


3. For running multiple tests and handling errors, you can use the following pattern:

```clojure
(->> test-data
     (pmap
      (fn [[k v]]
        [k
         (try-with-error-handling
          #(run-test test-data k step-function-map))]))
     (doall)
     (into {}))
```


This will run all tests defined in `test-data` and return a map of test results.

## Customization

You can extend the `step-function-map` in the `websimulate.core` namespace to add custom actions or override existing ones.

## License

Copyright © 2024

This program and the accompanying materials are made available under the terms of the Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0.
This Source Code may also be made available under the following Secondary Licenses when the conditions for such availability set forth in the Eclipse Public License, v. 2.0 are satisfied: GNU General Public License as published by the Free Software Foundation, either version 2 of the License, or (at your option) any later version, with the GNU Classpath Exception which is available at https://www.gnu.org/software/classpath/license.html.