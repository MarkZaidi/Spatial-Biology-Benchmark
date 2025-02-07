# Spatial-Biology-Benchmark
A QuPath Benchmark to evaluate workstation performance for processing of spatial biology datasets.

![cover art](https://github.com/MarkZaidi/Spatial-Biology-Benchmark/assets/52012166/863fb2d7-4c82-4ce6-a843-d47855277ab8)

This script was developed to test common elements of a spatial biology workflow applied to a large multiplexed immunohistochemistry image. This can be considered a more comphrensive and advanced version of [QuMark](https://github.com/MarkZaidi/QuMark), with a few additional changed to thoroughly test each element of a workstation's hardware, allowing the user to identify any rate limiting components of their system and address them accordingly. 

## Stages
  The benchmark consists of the following stages:
  - Cell segmentation
      - This consists of a pretrained StarDist model inference (GPU-acceleratable), follwed by calculations
        of intensity features (non GPU-acceleratable)
      - QuPath must be built from source with CUDA support, as outlined here: https://qupath.readthedocs.io/en/stable/docs/reference/building.html#building-from-source
      - Nvidia GPUs are required for this. If no CUDA-capable GPU is found, model inference will default to the CPU
      - A "cellular checksum" will be performed to verify that the total number of cells detected matches what is expected.
        Differences in cell counts typically points to one or more tiles failing to process.
  - Composite classification
      - A composite classifier included in the project will be applied to the cells
  - Area segmentation
      - A pretrained pixel classifier will be used to create annotaton objects of tumor and stroma
  - Spatial feature augmentation
      - Spatial features for all cells will be generated and timed together. This includes:
          - Annotation distances
          - Cell distances (using base class) - this has been disabled due to a bug in QuPath causing it to never finish for large MxIF images
          - Delaunay Triangulation
      - All of these are CPU-heavy functions, and will likely be the rate limiting factor of the benchmark
      - Cell distances (detection centroids) takes extremely long, may omit from final revision of the benchmark
  - Measurement export
      - Per cell statistics will be exported and timed, measuring the write performance of the storage device
## Output
The output of running this script will be a `timings_xxx.txt` file. Each line will contain the output of a specific test or system information. Shown below is an example file, with a description of each line:

`Spatial Biology Benchmark version 2024_02_26` - the version of the benchmark script used
 
`benchmark image - full` - the name of the project entry for which the benchmark was run. See `Usage` for the different options

`Cell Detection: 1 hours, 15 minutes, 14.990 seconds` - Duration of cell detection, a CPU, memory, storage, and (optionally) GPU-intensive process. This will be the longest test.

`Cell Classification: 1 minutes, 15.173 seconds` - applying a composite classifier. This should take no longer than a few minutes

`Area segmentation: 12.501 seconds` - applying a low resolution pixel classifier. This should take no longer than a few seconds.

`Spatial Analysis: 6 minutes, 12.762 seconds` - Calculations of annotation distances and delaunay triangulation. Should take longer than area segmentation and cell classification combined.

`Measurement Export: 7 minutes, 12.016 seconds` - Writing per-cell data to the storage device. By default, this will be the project base directory, so make sure the project is saved on a fast storage device (e.g. SSD)
 
`Overall time: 1 hours, 30 minutes, 7.463 seconds` - overall length of test

`Version: 0.5.0-rc1` - version of QuPath used

`Build time: 2023-10-07, 11:08` - build time of QuPath build

`Cell Count Verification: PASS` - a sort of "cellular checksum" which is set to `PASS` if the number of detected cells matches what's expected.

## Installation and Usage
1. Build QuPath from source, with CUDA if you have a CUDA-compatible GPU (most modern Nvidia GPUs), as outlined here: https://qupath.readthedocs.io/en/stable/docs/reference/building.html. Tested with 0.5.0-rc1.
2. Download and install StarDist Extension for QuPath: https://github.com/qupath/qupath-extension-stardist. Tested with 0.4.0.
3. Download the `DSB2018_heavy_augment.pb` stardist model from here: https://github.com/qupath/models/raw/main/stardist/dsb2018_heavy_augment.pb
4. Download the `Spatial Biology Benchmark` folder from this repository. This is a QuPath project that contains all the relevant scripts, classifiers, and project metadata required to be able to run the benchmark
5. In `Benchmark Project\scripts\Spatial biology benchmarking script v1.groovy`, set `modelPath` to point to the path of your downloaded `DSB2018_heavy_augment.pb` stardist model
6. Download the benchmark image `Multiplexed image.qptiff`. I haven't decided on a good way to host this, so for now, just shoot me an email and I'll send it to you via Google Drive or something.
7. Open the QuPath project in QuPath. During the first time opening this, QuPath will prompt you for the location of `Multiplexed image.qptiff`, so you'll have to set that to wherever the image is stored. Again, make sure both the project and image are stored on a fast storage device like an SSD. Or if you're really crazy, 4 Gen 5 NVMes in a PCIe x16 slot, all in RAID 0.
8. Without opening the project entry, run the script `Spatial biology benchmarking script v1.groovy` on the desired benchmarking project entry. There are 4 entries to choose from: `benchmark - full`, `benchmark - large`, `benchmark - medium`, and `benchmark - small`. The smaller the test, the faster it will run, but the less representative it will be of a true multiplexed WSI. `benchmark - full` is recommended, and can take anywhere from 1 hour to overnight, depending on your system specs. If you want to just verify that this script will run, use `benchmark - small`.
9. Review the generated `timings_xxx.txt` and confirm that the cell count verification has passed.
10. Share the results with your colleagues or on social media!



