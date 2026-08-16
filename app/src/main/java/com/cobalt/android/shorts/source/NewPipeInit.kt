package com.cobalt.android.shorts.source

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import java.util.concurrent.atomic.AtomicBoolean

/** NewPipeExtractor is a process-wide singleton; it must be initialized exactly once. */
object NewPipeInit {
    private val initialized = AtomicBoolean(false)

    fun ensureInitialized() {
        if (initialized.compareAndSet(false, true)) {
            NewPipe.init(
                OkHttpNewPipeDownloader.getInstance(),
                Localization("en", "US"),
                ContentCountry("US")
            )
        }
    }
}
