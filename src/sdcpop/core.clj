(ns sdcpop.core
  (:require
   [clj-yaml.core]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [cheshire.core :as json])
  (:gen-class))

(def manifest-name "manifest.yaml")

(def known-extensions
  {:itemControl {:url "http://hl7.org/fhir/StructureDefinition/questionnaire-itemControl"
                 :type "CodeableConcept"
                 :template {:system "http://hl7.org/fhir/questionnaire-item-control"}
                 :template-path [:code]}
   :path {:url "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-initialExpression"
          :type "Expression"
          :template {:language "text/fhirpath"}
          :template-path [:expression]}
   :calculatedExpression {:url "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-calculatedExpression"
                          :type "Expression"
                          :template {:language "text/fhirpath"}
                          :template-path [:expression]}})

(defn extension
  [[k v]]
  (let [ext-data (get known-extensions k)]
    (merge (dissoc ext-data :type :template :template-path)
           {(keyword (str "value" (:type ext-data))) 
            (assoc-in (get ext-data :template) 
                      (get ext-data :template-path) 
                      v)})))

(defn structure-definition-url
  [d]
  (let [res-type (second (re-find #"(^[^/]+)/" d))
        path (if (str/ends-with? d ".yaml") res-type d)]
    (str "http://hl7.org/fhir/StructureDefinition/" path)))

(defn item
  [itm]
  (reduce-kv
   (fn [m k v]
     (case k
       :linkId (assoc m k (get itm :linkId (str (java.util.UUID/randomUUID))))
       (:text :type :required :repeats) (assoc m k v)
       :extension (assoc m k (mapv extension
                                   (select-keys itm [:itemControl
                                                     :path
                                                     :calculatedExpression])))
       :items (assoc m k (mapv item (get itm :items)))
       :definition (assoc m k (structure-definition-url (get itm :definition)))
       ;; all other cases just ignored
       m))
   {}
   itm))

(defn questionnaire
  "Builds questionnaire from parsed yaml"
  [title definition manifest]
  {:resourceType "Questionnaire"
   :url (str (:url manifest) "/Questionnaire/" (:id manifest) "-" title)
   :version (:version manifest)
   :title (:title definition)
   :status "active"
   :item (map item
              (get definition :items))})

(defn read-manifest
  "Parse manifest file"
  [dir]
  (->> (io/file dir manifest-name)
       (slurp)
       (clj-yaml.core/parse-string)))

(defn read-definitions
  "Reads dir path to a map of file-name => parsed yaml"
  [dir]
  (let [dir (.getAbsoluteFile (io/file dir))]
    (->> dir
         (file-seq)
         (remove #(.isDirectory %))
         (remove #(= manifest-name (.getName %)))
         (filter #(= (.getPath dir) (.. % getAbsoluteFile getParent)))
         (map (fn [f] [(str/replace (.getName f) #"\.yaml$" "")
                       (-> (.getAbsolutePath f)
                           (slurp)
                           (clj-yaml.core/parse-string))]))
         (into {}))))

(defn process-definitions
  "Generate Questionnaires for definitions"
  [defs manifest]
  (->> defs
       (map (fn [[title definition]]
              [title (json/generate-string
                      (questionnaire title definition manifest))]))
       (into {})))

(defn -main
  [& args]
  (process-definitions (read-definitions (first args)) (read-manifest (first args))))

(comment
  (str "aaa" ".xx")
  (def manifest (read-manifest "./examples/example-project-1"))
  (def title "xxx")
  (json/generate-string {:foo "bar" :baz 5})
  (-main "./examples/example-project-1")
  (get {} :key :xxx)
  (java.util.UUID/randomUUID)

  (clojure.pprint/pprint (read-definitions "./examples/example-project-1"))
  (->> (get-in (read-definitions "./examples/example-project-1")
               ["example-questionnaire" :items])
       (map item)
       (clojure.pprint/pprint))

  ;; https://clojuredocs.org/clojure.string/split
  (map (fn rule [r]
         (let [parts (str/split r #"\s+")]
           (if (= 3 (count parts))
             {:linkId (first parts)
              :operator (second parts)
              :third (last parts)}
             parts)))
       ["weight exists" "height = 185"])


  (def home (io/file "./examples/example-project-1"))
  (def files (file-seq home))

  (def definitions (->> files
                        (remove #(.isDirectory %))
                        (remove #(= manifest-name (.getName %)))
                        (filter #(= (.getPath home) (.getParent %)))
                        (map (fn [f] [(.getName f)
                                      (-> (.getAbsolutePath f)
                                          (slurp)
                                          (clj-yaml.core/parse-string))]))
                        (into {}))))