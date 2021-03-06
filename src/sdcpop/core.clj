(ns sdcpop.core
  (:require
   [clj-yaml.core]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [cheshire.core :as json])
  (:gen-class))

(def manifest-name "manifest.yaml")

(def type-map {"choice" "CodeableConcept"
               "open-choice" "CodeableConcept"
               "text" "string"
               "url" "string"})

(def known-extensions
  {:itemControl
   {:url "http://hl7.org/fhir/StructureDefinition/questionnaire-itemControl"
    :type "CodeableConcept"
    :template {:coding {:system "http://hl7.org/fhir/questionnaire-item-control"}}
    :template-path [:coding :code]}
   
   :path
   {:url "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-initialExpression"
    :type "Expression"
    :template {:language "text/fhirpath"}
    :template-path [:expression]}

   :calculatedExpression
   {:url "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-calculatedExpression"
    :type "Expression"
    :template {:language "text/fhirpath"}
    :template-path [:expression]}})

(defn extension
  [[k v]]
  (let [ext-data (get known-extensions k)]
    (assoc (select-keys ext-data [:url])
           (keyword (str "value" (get ext-data :type)))
           (assoc-in (get ext-data :template)
                     (get ext-data :template-path) v))))

(defn structure-definition-url
  [d]
  (let [res-type (second (re-find #"(^[^/]+)/" d))
        path (if (str/ends-with? d ".yaml") res-type d)]
    (str "http://hl7.org/fhir/StructureDefinition/" path)))

(defn fhir-type
  [t]
  (get type-map t t))

(defn parse-number
  [s]
  (let [n (read-string s)]
    (if (number? n) n nil)))

(defn parse-boolean
  [s]
  (let [n (read-string s)]
    (if (boolean? n) n nil)))

(defn typecast-literal
  [type s]
  (case type
    ("decimal" "integer" "unsignedInt" "positiveInt") (parse-number s)
    "boolean" (parse-boolean s)))

(defn enable-when
  [conditions meta]
  (map
   (fn rule [r]
     (let [parts (str/split r #"\s+")]
       (if (= 3 (count parts))
         (let [type (fhir-type (get meta (first parts)))]
           {:question (first parts)
            :operator (second parts)
            (keyword (str "answer" (str/capitalize type))) (typecast-literal type (last parts))})
         {:question (first parts)
          :operator (second parts)})))
   conditions))

(defn item
  "Takes an item as an input and returns an item map where some keys are preprocessed."
  [meta itm]
  (let [linkId (get itm :linkId (str (java.util.UUID/randomUUID)))
        dfn (get itm :definition (get meta :definition ""))
        meta' (assoc meta linkId (get itm :type) :definition dfn)]
    (->> (merge (assoc itm :linkId linkId)
                (when (seq (select-keys itm (keys known-extensions)))
                  {:extension (mapv extension (select-keys itm (keys known-extensions)))}))
         (reduce-kv
          (fn [m k v]
            (case k
              ;; ignore extension and definition keys
              (:path :calculatedExpression :itemControl :definition) m
              ;; some keys need special treatment
              :answers (if (coll? v)
                         (assoc m :answerOption (mapv #(hash-map :valueCoding %) v))
                         (assoc m :answerValueSet v))
              :enableWhen (assoc m k (enable-when v meta'))
              :elementId (assoc m :definition (str (structure-definition-url (:definition meta')) "#" v))
              :items (assoc m
                            :item
                            (loop [meta' meta'
                                   items v
                                   res []]
                              (if (seq items)
                                (let [it (first items)
                                      linkId (get it :linkId (str (java.util.UUID/randomUUID)))
                                      meta' (assoc meta' linkId (get it :type))]
                                  (recur
                                   meta'
                                   (rest items)
                                   (conj res (item meta' it))))
                                res)))
              ;; all other keys just copied over
              (assoc m k v)))
          {}))))

(defn questionnaire
  "Builds questionnaire from parsed yaml"
  [title definition manifest]
  {:resourceType "Questionnaire"
   :url (str (:url manifest) "/Questionnaire/" (:id manifest) "-" title)
   :version (:version manifest)
   :title (:title definition)
   :status "active"
   :item (mapv (partial item {})
               (get definition :items))})

(defn read-manifest
  "Parse manifest file"
  [dir]
  (->> (io/file dir manifest-name)
       (slurp)
       (clj-yaml.core/parse-string)))

(defn read-definitions
  "Reads dir path to a map of file-name => JSON"
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
  [manifest defs]
  (->> defs
       (map (fn [[title definition]]
              [title (-> (questionnaire title definition manifest)
                         (json/generate-string {:pretty true}))]))
       (into {})))

(defn write-questionnaires
  [dir qs]
  (doseq [q qs]
    (spit (.getAbsolutePath (io/file dir (str (first q) ".json")))
          (second q))))

(defn -main
  [& args]
  (->> (read-definitions (first args))
       (process-definitions (read-manifest (first args)))
       (write-questionnaires (second args))))

(comment
  (-main "./examples/example-project-1" "./output")
)
