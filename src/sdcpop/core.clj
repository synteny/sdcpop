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

(defn fhir-type
  [t]
  (get type-map t t))

(defn enable-when
  [conditions meta]
  (map
   (fn rule [r]
     (let [parts (str/split r #"\s+")]
      ;  (prn :wtf (first parts) meta)
       (if (= 3 (count parts))
         {:linkId (first parts)
          :operator (second parts)
          (keyword (str "answer" (str/capitalize (fhir-type (get meta (first parts)))))) (last parts)}
         {:linkId (first parts)
          :operator (second parts)})))
   conditions))

(defn item
  "Takes an item as an input and returns an item map where some keys are preprocessed."
  [meta itm]
  (let [linkId (get itm :linkId (str (java.util.UUID/randomUUID)))
        meta' (assoc meta linkId (get itm :type))]
    (->> (merge (assoc itm :linkId linkId)
                (when (seq (select-keys itm (keys known-extensions)))
                  {:extension (mapv extension (select-keys itm (keys known-extensions)))}))
         (reduce-kv
          (fn [m k v]
            (case k
              ;; ignore extension keys
              (:path :calculatedExpression :itemControl) m
              ;; some keys need a special treatment
              :answers (assoc m :answerOption (if (coll? v)
                                                (mapv #(hash-map :valueCoding (get % :code)) v)
                                                v))
              :enableWhen (assoc m k (enable-when v meta'))
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
              :definition (assoc m k (structure-definition-url v))
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
