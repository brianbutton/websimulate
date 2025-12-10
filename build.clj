(ns build
  (:refer-clojure :exclude [test])
  (:require [clojure.tools.build.api :as b]))

(def lib 'io.github.brianbutton/websimulate)
(def version "0.1.0")
(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))

;; Delay to defer side effects (artifact downloads)
(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean
  "Delete the build target directory."
  [_]
  (b/delete {:path "target"}))

(defn test
  "Run all the tests."
  [opts]
  (let [basis    (b/create-basis {:aliases [:test]})
        cmds     (b/java-command
                  {:basis     basis
                   :main      'clojure.main
                   :main-args ["-m" "cognitect.test-runner"]})
        {:keys [exit]} (b/process cmds)]
    (when-not (zero? exit) (throw (ex-info "Tests failed" {}))))
  opts)

(defn jar
  "Build the library JAR file."
  [_]
  (b/delete {:path "target"})
  (let [basis @basis]
    (println "Writing pom.xml...")
    (b/write-pom {:class-dir class-dir
                  :lib lib
                  :version version
                  :basis basis
                  :src-dirs ["src"]
                  :scm {:url "https://github.com/brianbutton/websimulate"
                        :connection "scm:git:git://github.com/brianbutton/websimulate.git"
                        :developerConnection "scm:git:ssh://git@github.com/brianbutton/websimulate.git"
                        :tag (str "v" version)}
                  :pom-data [[:description "A Clojure library for declarative web browser automation and simulation"]
                             [:url "https://github.com/brianbutton/websimulate"]
                             [:licenses
                              [:license
                               [:name "Eclipse Public License 1.0"]
                               [:url "https://opensource.org/licenses/EPL-1.0"]]]]})
    (println "Copying source...")
    (b/copy-dir {:src-dirs ["src" "resources"]
                 :target-dir class-dir})
    (println (str "Building JAR: " jar-file))
    (b/jar {:class-dir class-dir
            :jar-file jar-file})))

(defn install
  "Install the JAR to local Maven repository."
  [_]
  (jar nil)
  (println "Installing to local Maven repo...")
  (b/install {:basis @basis
              :lib lib
              :version version
              :jar-file jar-file
              :class-dir class-dir}))

(defn ci
  "Run the CI pipeline: tests + build JAR."
  [opts]
  (test opts)
  (jar opts)
  opts)

(defn deploy
  "Deploy to Clojars. Requires CLOJARS_USERNAME and CLOJARS_PASSWORD env vars."
  [_]
  (jar nil)
  (println "Deploying to Clojars...")
  ((requiring-resolve 'deps-deploy.deps-deploy/deploy)
   {:installer :remote
    :artifact jar-file
    :pom-file (str class-dir "/META-INF/maven/" (namespace lib) "/" (name lib) "/pom.xml")}))
