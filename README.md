# Shogun
UTF-8 Text Based Compression for Short Texts and Protocols using subset Charsets of UTF-8.

# Mechanism
It uses a Naive substitution of frequent char sequences for unused chars within UTF-8, that 

# Calculation of Substitution Dictionary

# Dictionary Format
The dictionary consists in a HashMap<String, Int> that can be represented in a JSON file, containing the String and Integer pairs.
I.E.:

```json
{  
   "http://www.webrtc.org/experiments/rtp-hdrext/":138,
   "sendrecv":147,
   "\na=":148,
   " goog-remb\n":165,
   "fmtp:":158
}
```

# Usage Example

```kotlin
val testInput = "JingleNodesJingleNodesJingleTestNodesTestFinalNodesJingle"
val c = Shogun.crunch(testInput, 4, 30, 6, Charsets.US_ASCII)

println("Crunched Test Input Text: ${c.crunched}")
println("Best Dictionary: ${c.dict}")
println("Dictionary Export: ${ShogunUtils.exportDict(c.dict)}")

```
