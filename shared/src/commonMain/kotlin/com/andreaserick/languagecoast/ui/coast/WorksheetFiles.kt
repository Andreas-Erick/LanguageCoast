package com.andreaserick.languagecoast.ui.coast

import androidx.compose.runtime.Composable
import com.andreaserick.languagecoast.data.WorksheetPdf

/** What became of saving a practice sheet to the phone. */
sealed interface WorksheetDownload {
    /** It is saved. [place] names where for the user ("Downloads"); null when they picked the place themselves. */
    data class Saved(val place: String?) : WorksheetDownload

    /** The user backed out of choosing where to save it. */
    data object Cancelled : WorksheetDownload

    data object Failed : WorksheetDownload
}

/**
 * Hands a finished practice sheet to the user.
 *
 * @property download Saves the PDF where the phone keeps downloads and reports how that went.
 * @property open Shows the PDF in another app; false if the phone has no app for PDFs.
 */
class WorksheetFiles(
    val download: (pdf: WorksheetPdf, onResult: (WorksheetDownload) -> Unit) -> Unit,
    val open: (pdf: WorksheetPdf) -> Boolean
)

/** This platform's way of saving and opening practice sheets; null where there is none yet (iPhone). */
@Composable
expect fun rememberWorksheetFiles(): WorksheetFiles?
