/** Spatial Biology benchmarking script, validated for QuPath version 0.5.0-RC1 and StarDist Extension version 0.4.0
 * Before running the test, ensure you have the correct version of QuPath and the StarDist extension, and have
 * verified that the StarDist segmentation works on your system.
 *
 * The benchmark consists of the following stages:
 * - Cell segmentation
 *     - This consists of a pretrained StarDist model inference (GPU-acceleratable), follwed by calculations
 *       of intensity features (non GPU-acceleratable)
 *     - QuPath must be built from source with CUDA support, as outlined here: https://qupath.readthedocs.io/en/stable/docs/reference/building.html#building-from-source
 *     - Nvidia GPUs are required for this. If no CUDA-capable GPU is found, model inference will default to the CPU
 *     - A "cellular checksum" will be performed to verify that the total number of cells detected matches what is expected.
 *       Differences in cell counts typically points to one or more tiles failing to process.
 * - Composite classification
 *     - A composite classifier included in the project will be applied to the cells
 * - Area segmentation
 *     - A pretrained pixel classifier will be used to create annotaton objects of tumor and stroma
 * - Spatial feature augmentation
 *     - Spatial features for all cells will be generated and timed together. This includes:
 *         - Annotation distances
 *         - Cell distances (using base class) - this has been disabled due to a bug in QuPath causing it to never finish for large MxIF images
 *         - Delaunay Triangulation
 *     - All of these are CPU-heavy functions, and will likely be the rate limiting factor of the benchmark
 *     - Cell distances (detection centroids) takes extremely long, may omit from final revision of the benchmark
 * - Measurement export
 *     - Per cell statistics will be exported and timed, measuring the write performance of the storage device
 */
 //Import libraries
import qupath.ext.stardist.StarDist2D
import qupath.lib.scripting.QP
import org.codehaus.groovy.control.messages.WarningMessage
import qupath.lib.gui.tools.MeasurementExporter
import qupath.lib.objects.PathCellObject
import groovy.time.*
import static qupath.lib.gui.scripting.QPEx.*


clearAllObjects();
 //Configuration (not timed)
 //Specify the path to the StarDist model dsb2018_heavy_augment.pb
 def modelPath = "//TRUENAS/Client_Datasets/00001_utilities/Stardist Trained Models/dsb2018_heavy_augment.pb"
 //Get resolution
 img_resolution=getCurrentImageData().getServer().getPixelCalibration().getAveragedPixelSizeMicrons() //Get the current image's resolution
 //Clear cache before running the benchmark
def store =  QuPathGUI.getInstance().getViewer().getImageRegionStore()
try {
    print "Clearing cache..."
    store.cache.clear()
    store.thumbnailCache.clear()
    System.gc()
} catch (Exception e2) {
    e2.printStackTrace()
}
//Clear out any preexisting stuff, create a whole image annotation
clearAllObjects(); //I'm just paranoid because object connections tend to linger
createFullImageAnnotation(true)
//Start total timer
def timeStart_total = new Date()




//Cell detection
print('Detecting cells')
def timeStart_CellDetection = new Date()
selectAnnotations();
 
 def stardist = StarDist2D
    .builder(modelPath)
    .channels('DAPI')            // Extract channel called 'DAPI'
    .normalizePercentiles(1, 99) // Percentile normalization
    .threshold(0.5)              // Probability (detection) threshold
    .pixelSize(0.25)              // Resolution for detection
    .cellExpansion(5)            // Expand nuclei to approximate cell boundaries
    .measureShape()              // Add shape measurements
    .measureIntensity()          // Add cell measurements (in all compartments)
    .tileSize(1024)
    .constrainToParent(true)
    .build()
	
// Define which objects will be used as the 'parents' for detection
// Use QP.getAnnotationObjects() if you want to use all annotations, rather than selected objects
def pathObjects = QP.getSelectedObjects()

// Run detection for the selected objects
def imageData = QP.getCurrentImageData()
if (pathObjects.isEmpty()) {
    QP.getLogger().error("No parent objects are selected!")
    return
}
stardist.detectObjects(imageData, pathObjects)
stardist.close() // This can help clean up & regain memory
TimeDuration CellDetection_duration = TimeCategory.minus(new Date(), timeStart_CellDetection)
println('Cell detection: '+CellDetection_duration)




//Classify cells
println('Classifying cells')
def timeStart_CellClassification = new Date()
runObjectClassifier("composite_classifier_v1");
TimeDuration CellClassification_duration = TimeCategory.minus(new Date(), timeStart_CellClassification)
println('Cell classification: '+CellClassification_duration)

//Area segmentation
println('Creating annotations from preset pixel classifier')
def timeStart_PixelClassifier = new Date()
selectObjectsByClassification(null);
clearSelectedObjects(true);
createAnnotationsFromPixelClassifier("PCK_classifier", 1000.0, 1000.0)
resolveHierarchy()
TimeDuration PixelClassifier_duration = TimeCategory.minus(new Date(), timeStart_PixelClassifier)
println('Area segmentation: '+PixelClassifier_duration)




//Spatial analysis
def timeStart_SpatialAnalysis = new Date()
println('Spatial analysis - annotation distance')
detectionToAnnotationDistances(true)

//println('Spatial analysis - centroid distance')
//detectionCentroidDistances(true) //Currently bugged, will never finish computation
println('Spatial analysis - delaunay triangulation')
selectAnnotations();
runPlugin('qupath.opencv.features.DelaunayClusteringPlugin', '{"distanceThresholdMicrons":7.0,"limitByClass":false,"addClusterMeasurements":true}')

TimeDuration SpatialAnalysis_duration = TimeCategory.minus(new Date(), timeStart_SpatialAnalysis)
println('Spatial analysis: '+SpatialAnalysis_duration)




//Export measurements as csv file
println('Exporting measurements')
def timeStart_MeasurementExport = new Date()

def name = getProjectEntry().getImageName() + '.csv'
def path = buildFilePath(PROJECT_BASE_DIR)
mkdirs(path)
path = buildFilePath(path, 'benchmark_measurements.csv')
saveDetectionMeasurements(path)


println "Done!"
TimeDuration MeasurementExport_duration = TimeCategory.minus(new Date(), timeStart_MeasurementExport)

//Calculate, consolidate, and write timings to .txt file in project base directory

TimeDuration total_duration = TimeCategory.minus(new Date(), timeStart_total)
println('Total duration: '+total_duration)
//check that total number of detections meets the expected amount

detections = getDetectionObjects()
//Should be a predetermined constant for the number of cells that should be detected in the benchmark

//Delete object connections because I hate them so, so much
def imData = getCurrentImageData()

def connections = imData.getProperties().get('OBJECT_CONNECTIONS')
describe(connections)
connections.clear()


//Begin writing timings to file
File timings =new File(buildFilePath(PROJECT_BASE_DIR,"timings_"+timeStart_total.getTime()+".txt"))

timings.append("Spatial Biology Benchmark version 2024_02_26" + "\n \n")
timings.append(getCurrentImageName()+"\n")
timings.append("Cell Detection: " + CellDetection_duration + "\n")
timings.append("Cell Classification: " + CellClassification_duration + "\n")
timings.append("Area segmentation: " + PixelClassifier_duration + "\n")
timings.append("Spatial Analysis: " + SpatialAnalysis_duration + "\n")
timings.append("Measurement Export: " + MeasurementExport_duration + "\n \n")
timings.append("Overall time: " + total_duration + "\n")
timings.append(getQuPath().getBuildString() + "\n")

// Define the expected detections for each project entry
def expected_detections_full = 3130999
def expected_detections_large = 1950645
def expected_detections_medium = 533525
def expected_detections_small = 5625

// Define the actual detections
def actual_detections = detections.size()

// Check if actual detections matches any of the expected detections
if (actual_detections == expected_detections_full) {
    println("Actual detections match the expected full detections.")
    timings.append('Cell Count Verification: PASS\n')
} else if (actual_detections == expected_detections_large) {
    println("Actual detections match the expected large detections.")
    timings.append('Cell Count Verification: PASS\n')
} else if (actual_detections == expected_detections_medium) {
    println("Actual detections match the expected medium detections.")
    timings.append('Cell Count Verification: PASS\n')
} else if (actual_detections == expected_detections_small) {
    println("Actual detections match the expected small detections.")
    timings.append('Cell Count Verification: PASS\n')
} else {
    println("Actual detections do not match any of the expected detections.")
    timings.append('Cell Count Verification: FAIL\n')
}









