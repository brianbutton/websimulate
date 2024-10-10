# websimulate

websimulate is a Clojure library designed to simulate web interactions and run automated tests using the Etaoin WebDriver library. It provides a flexible framework for defining and executing web-based test scenarios.

## Features

- Easy-to-define test scenarios using a map-based structure
- Built-in support for common web interactions (navigation, clicking, filling forms, etc.)
- Conditional steps for more complex test flows
- Screenshot capture capability with automatic timestamping
- Extensible step function map for custom actions
- Global debug mode and delay settings
- Error handling and reporting

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

1. Define your test scenarios in the `test-data` map within your test file. For example:


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

You can extend the `step-function-map` in the `websimulate.core` namespace to add custom actions or override existing ones. The current `step-function-map` includes actions like:

- `:go` - Navigate to a URL
- `:wait-visible` - Wait for an element to become visible
- `:fill` - Fill in a form field
- `:wait-has-text-everywhere` - Wait for text to appear anywhere on the page
- `:wait` - Wait for a specified duration
- `:query` - Query for an element
- `:click` - Click on an element
- `:get-element-text` - Get the text of an element
- `:screenshot` - Take a screenshot with automatic timestamping
- `:println` - Print a message
- `:conditional` - Perform conditional steps

## Configuration

You can adjust global settings in the `websimulate.core` namespace:

- `global-debug`: Set to `true` to enable debug output
- `global-screenshot-folder`: Set the folder for saving screenshots
- `global-delay`: Set a global delay to be added to wait times
- `global-delay-override`: Override all wait times with a fixed value

## Error Handling

The `try-with-error-handling` function wraps test execution and provides detailed error messages if a test fails.

## License

Copyright © 2024

This program and the accompanying materials are made available under the terms of the Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0.
This Source Code may also be made available under the following Secondary Licenses when the conditions for such availability set forth in the Eclipse Public License, v. 2.0 are satisfied: GNU General Public License as published by the Free Software Foundation, either version 2 of the License, or (at your option) any later version, with the GNU Classpath Exception which is available at https://www.gnu.org/software/classpath/license.html.