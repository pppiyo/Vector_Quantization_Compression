# Vector Quantization Image Compression

## About
Java implementation of vector quantization compression method for images, using kmeans clustering algorithm.

## How to run
1. Put VectorCompression.java file as well as the one-channel rgb image (e.g. "image1-onechannel.rgb") under the same folder.

2. In terminal, run command:
```
javac VectorCompression.java
java VectorCompression <IMAGE> <SIZE> <NUMBER>
```
**IMAGE**  :  The one-channel rgb image file name. <br>
**SIZE**   :  Any integer. Size of a vector you pick, a.k.a number of adjacent pixels side by side as a vector unit. <br>
**NUMBER** :  Any integer. Number of vectors of your choice. Making it a power of 2 can maximize usage of available indexes.
  
## Expected outcomes
After running the program, original image and compressed version will be juxtaposed in a pop-up window as show below:

SIZE = 2, NUMBER = 16
<img width="816" alt="image" src="https://github.com/pppiyo/Vector_Quantization_Compression/assets/31379013/e2a33d35-7418-4744-b293-844f49e4d43c">

SIZE = 3, NUMBER = 16
<img width="816" alt="image" src="https://github.com/pppiyo/Vector_Quantization_Compression/assets/31379013/8d0e7857-c95d-47f4-baa9-0458023654b1">

SIZE = 3, NUMBER = 30
<img width="816" alt="image" src="https://github.com/pppiyo/Vector_Quantization_Compression/assets/31379013/3231df4e-b727-4862-8c9f-1295da9266c3">

SIZE = 7, NUMBER = 30
<img width="816" alt="image" src="https://github.com/pppiyo/Vector_Quantization_Compression/assets/31379013/6376386a-c4aa-44a1-8aa9-245e3a31d0ee">

As an observation, compressed image will be more refined with the increase of NUMBER and the decrease of SIZE.

# Idiosyncrasies
1. This program supports one-channel rgb image file comepression only. 
2. Size of image is limited to 352 x 288.

