package io.moonbase.crosspair

/**
 * Process-wide controller handle used by notification actions. When the app
 * process is alive, Accept/Decline/Cancel can be handled without opening the
 * activity. A cold process falls back to launching MainActivity.
 */
object CrossPairControllerHolder {
    @Volatile
    var current: CrossPairController? = null
}
