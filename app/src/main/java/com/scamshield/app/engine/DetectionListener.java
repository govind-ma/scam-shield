package com.scamshield.app.engine;

/**
 * DetectionListener — callback interface for receiving analysis results.
 *
 * LOCKED SHAPE — defined in PROJECT_CONTEXT.md.
 * Do not rename this method or change its signature.
 *
 * Any class that wants to react to detection results (e.g. ScamAlertManager)
 * implements this interface and registers itself with the sensor.
 */
public interface DetectionListener {

    /**
     * Called when a sensor has analysed a message and produced a result.
     *
     * @param result The fully populated DetectionResult from the engine.
     */
    void onResult(DetectionResult result);
}
