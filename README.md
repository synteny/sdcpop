# SDCPOP: FHIR Questionnaires and profiles for SDC built from simple YAML markup

## Introduction

This project builds on top of [IGPOP](http://github.com/healthsamurai/igpop) to provide an easy way of generating [FHIR SDC](http://hl7.org/fhir/us/sdc) conformant Questionnaires with accompanying profiles from YAML markup.

> **WARNING**: IGPOP does not yet output StructureDefinitions in JSON. Until this is implemented, we only build Questionnaires, and ignore YAML profile definitions. For the sake of completeness, we describe the full project structure below, but not all of it is used yet.

## Input data

Every project begins with a manifest, setting global properties:
```yaml
id: SDCPOP-examples
title: Blood Pressure Questionnaire
url: http://example.org/fhir
version: 1.0.0
description: Blood Pressure Example for SDCPOP
fhir: 4.0.0
```

Then, we define the Questionnaire:
```yaml
description: Blood pressure systolic & diastolic
title: Blood Pressure
items:
  - text: Blood pressure
    type: group
    definition: Observation/blood-pressure.yaml
    items:
      - text: Systolic
        type: decimal
        itemControl: textbox
        path: 'Observation.component[0].value'
      - text: Diastolic
        type: decimal
        itemControl: textbox
        path: 'Observation.component[1].value'
```

* name of this file is used as ``Questionnaire.name``, and as part of ``Questionnaire.url``
* ``definition`` maps to ``Questionnaire.item.definition``. This may be:
    - Resource type name, e.g. 'Observation'. This resolves to the core Observation profile.
    - file path of IGPOP definition in yaml
* ``itemControl`` yields ``questionnaire-itemControl`` extension
* ``path`` yields ``questionnaire-initialExpression`` extension. This sets the resource path to map this item to.

We define profiles in IGPOP syntax. For every Questionnaire definition ``<filename>.yaml``, its profiles are placed under a correspondingly-named directory: ``<filename>-profiles``. Below that, normal IGPOP project structure is maintained:
```
\
manifest.yaml
blood-pressure.yaml
blood-pressure-profiles
  Observation
    blood-pressure.yaml
  <other resource type>
    <profiles for other resource type>
    ...
  ...
```

The IGPOP definition for blood pressure profile is the following:
```yaml
code:
  constant:
    coding:
      system: http://loinc.org
      code: 85354-9
component:
  slices:
    key: systolic
    elements:
      code:
        constant:
          coding:
            system: http://loinc.org
            code: 8480-6
      value:
        Quantity:
          unit:
            constant: mm[Hg]
    key: diastolic
    elements:
      code:
        constant:
          coding:
            system: http://loinc.org
            code: 8462-4
      value:
        Quantity:
          unit:
            constant: mm[Hg]
```

## Building and running
This project uses make:
```
make build
```

After build, locate the jar at ``target/sdcpop.jar``.

To run:
```
java -jar sdcpop.jar <project-dir> <output-dir>
```

If applied to our example above, this would result in the following Questionnaire:
```json
{
  "resourceType" : "Questionnaire",
  "url" : "http://example.org/fhir/Questionnaire/SDCPOP-examples-blood-pressure",
  "version" : "1.0.0",
  "title" : "Blood Pressure",
  "status" : "active",
  "item" : [ {
    "text" : "Blood pressure",
    "type" : "group",
    "definition" : "http://hl7.org/fhir/StructureDefinition/Observation",
    "item" : [ {
      "text" : "Systolic",
      "type" : "decimal",
      "linkId" : "c5b5615e-9f3c-497a-9424-0630bf85873b",
      "extension" : [ {
        "url" : "http://hl7.org/fhir/StructureDefinition/questionnaire-itemControl",
        "valueCodeableConcept" : {
          "coding" : {
            "system" : "http://hl7.org/fhir/questionnaire-item-control",
            "code" : "textbox"
          }
        }
      }, {
        "url" : "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-initialExpression",
        "valueExpression" : {
          "language" : "text/fhirpath",
          "expression" : "Observation.component[0].value"
        }
      } ]
    }, {
      "text" : "Diastolic",
      "type" : "decimal",
      "linkId" : "5cfdbe4e-4fce-4ebe-81d2-db1abebdaf2a",
      "extension" : [ {
        "url" : "http://hl7.org/fhir/StructureDefinition/questionnaire-itemControl",
        "valueCodeableConcept" : {
          "coding" : {
            "system" : "http://hl7.org/fhir/questionnaire-item-control",
            "code" : "textbox"
          }
        }
      }, {
        "url" : "http://hl7.org/fhir/uv/sdc/StructureDefinition/sdc-questionnaire-initialExpression",
        "valueExpression" : {
          "language" : "text/fhirpath",
          "expression" : "Observation.component[1].value"
        }
      } ]
    } ],
    "linkId" : "6bd54ec4-ebde-4d15-a8e5-499556c43f47"
  } ]
}
```