package com.example.movieapp.data.parser

import android.util.Log
import com.example.movieapp.data.models.DataSource
import com.example.movieapp.data.models.Movie
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.*

/**
 * processor csv robusto per imdb e letterboxd
 * parsing avanzato con gestione errori e validazione
 */
class CsvProcessor {

    private val TAG = "CsvProcessor"

    /**
     * parse imdb ratings.csv (watched list)
     */
    fun parseImdbWatchedCsv(inputStream: InputStream): CsvParseResult {
        Log.d(TAG, "=== parsing imdb ratings.csv ===")

        return try {
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))

            //apache commons csv con auto-detect delimiter
            val csvFormat = CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .setDelimiter(',')
                .build()

            val csvParser = CSVParser(reader, csvFormat)
            val movies = mutableListOf<Movie>()
            val errors = mutableListOf<String>()
            var successfulRows = 0

            Log.d(TAG, "headers rilevati: ${csvParser.headerNames.joinToString()}")

            csvParser.forEach { record ->
                try {
                    //verifica headers imdb
                    val hasRequiredHeaders = listOf(
                        "Title", "Year", "Your Rating", "Date Rated"
                    ).all { header ->
                        csvParser.headerNames.any { it.equals(header, ignoreCase = true) }
                    }

                    if (!hasRequiredHeaders && movies.isEmpty()) {
                        errors.add("file non valido: headers imdb mancanti")
                        return@forEach
                    }

                    //estrai campi con fallback case-insensitive
                    val title = getField(record, csvParser.headerNames, "Title", "title")
                    val yearStr = getField(record, csvParser.headerNames, "Year", "year")
                    val ratingStr = getField(record, csvParser.headerNames, "Your Rating", "your rating", "rating")
                    val dateRated = getField(record, csvParser.headerNames, "Date Rated", "date rated", "date")
                    val directorStr = getField(record, csvParser.headerNames, "Directors", "Director", "directors")

                    if (title.isNullOrBlank()) {
                        errors.add("riga ${record.recordNumber}: titolo vuoto")
                        return@forEach
                    }

                    //parse year con validazione
                    val year = try {
                        yearStr?.toIntOrNull()?.let { y ->
                            if (y in 1888..2030) y else null
                        }
                    } catch (e: Exception) {
                        null
                    }

                    //parse rating con validazione (0-10)
                    val userRating = try {
                        ratingStr?.toDoubleOrNull()?.let { r ->
                            if (r in 0.0..10.0) r else null
                        }
                    } catch (e: Exception) {
                        null
                    }

                    //parse director
                    val director = directorStr?.takeIf { it.isNotBlank() }

                    val movie = Movie(
                        id = generateMovieId(title, year, DataSource.IMDB),
                        title = title.trim(),
                        year = year,
                        userRating = userRating,
                        dateRated = dateRated?.trim(),
                        director = director,
                        isWatched = true,
                        source = DataSource.IMDB
                    )

                    movies.add(movie)
                    successfulRows++

                    if (successfulRows <= 5) {
                        Log.d(TAG, "✓ parsed: ${movie.title} (${movie.year}) - rating: ${movie.userRating}")
                    }

                } catch (e: Exception) {
                    val errorMsg = "riga ${record.recordNumber}: ${e.message}"
                    errors.add(errorMsg)
                    Log.w(TAG, errorMsg)
                }
            }

            csvParser.close()
            reader.close()

            Log.d(TAG, "=== parsing completato ===")
            Log.d(TAG, "successo: $successfulRows film")
            Log.d(TAG, "errori: ${errors.size}")

            if (movies.isEmpty() && errors.isNotEmpty()) {
                Log.e(TAG, "❌ nessun film valido trovato")
                errors.forEach { Log.e(TAG, "  • $it") }
            }

            CsvParseResult(
                movies = movies,
                successfulRows = successfulRows,
                errors = errors,
                totalRows = successfulRows + errors.size
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ errore parsing imdb ratings", e)
            CsvParseResult(
                movies = emptyList(),
                successfulRows = 0,
                errors = listOf("errore parsing: ${e.message}"),
                totalRows = 0
            )
        }
    }

    /**
     * parse imdb watchlist.csv
     */
    fun parseImdbWatchlistCsv(inputStream: InputStream): CsvParseResult {
        Log.d(TAG, "=== parsing imdb watchlist.csv ===")

        return try {
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))

            val csvFormat = CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .setDelimiter(',')
                .build()

            val csvParser = CSVParser(reader, csvFormat)
            val movies = mutableListOf<Movie>()
            val errors = mutableListOf<String>()
            var successfulRows = 0

            Log.d(TAG, "headers rilevati: ${csvParser.headerNames.joinToString()}")

            csvParser.forEach { record ->
                try {
                    //verifica headers watchlist
                    val hasRequiredHeaders = listOf(
                        "Title", "Year"
                    ).all { header ->
                        csvParser.headerNames.any { it.equals(header, ignoreCase = true) }
                    }

                    if (!hasRequiredHeaders && movies.isEmpty()) {
                        errors.add("file non valido: headers imdb watchlist mancanti")
                        return@forEach
                    }

                    val title = getField(record, csvParser.headerNames, "Title", "title")
                    val yearStr = getField(record, csvParser.headerNames, "Year", "year")
                    val directorStr = getField(record, csvParser.headerNames, "Directors", "Director", "directors")

                    if (title.isNullOrBlank()) {
                        errors.add("riga ${record.recordNumber}: titolo vuoto")
                        return@forEach
                    }

                    val year = try {
                        yearStr?.toIntOrNull()?.let { y ->
                            if (y in 1888..2030) y else null
                        }
                    } catch (e: Exception) {
                        null
                    }

                    val director = directorStr?.takeIf { it.isNotBlank() }

                    val movie = Movie(
                        id = generateMovieId(title, year, DataSource.IMDB),
                        title = title.trim(),
                        year = year,
                        director = director,
                        isWatched = false,
                        source = DataSource.IMDB
                    )

                    movies.add(movie)
                    successfulRows++

                    if (successfulRows <= 5) {
                        Log.d(TAG, "✓ parsed: ${movie.title} (${movie.year})")
                    }

                } catch (e: Exception) {
                    val errorMsg = "riga ${record.recordNumber}: ${e.message}"
                    errors.add(errorMsg)
                    Log.w(TAG, errorMsg)
                }
            }

            csvParser.close()
            reader.close()

            Log.d(TAG, "=== parsing completato ===")
            Log.d(TAG, "successo: $successfulRows film")
            Log.d(TAG, "errori: ${errors.size}")

            CsvParseResult(
                movies = movies,
                successfulRows = successfulRows,
                errors = errors,
                totalRows = successfulRows + errors.size
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ errore parsing imdb watchlist", e)
            CsvParseResult(
                movies = emptyList(),
                successfulRows = 0,
                errors = listOf("errore parsing: ${e.message}"),
                totalRows = 0
            )
        }
    }

    /**
     * parse letterboxd diary.csv (watched)
     */
    fun parseLetterboxdWatchedCsv(inputStream: InputStream): CsvParseResult {
        Log.d(TAG, "=== parsing letterboxd diary.csv ===")

        return try {
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))

            val csvFormat = CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .setDelimiter(',')
                .build()

            val csvParser = CSVParser(reader, csvFormat)
            val movies = mutableListOf<Movie>()
            val errors = mutableListOf<String>()
            var successfulRows = 0

            Log.d(TAG, "headers rilevati: ${csvParser.headerNames.joinToString()}")

            csvParser.forEach { record ->
                try {
                    //verifica headers letterboxd
                    val hasRequiredHeaders = listOf(
                        "Name", "Year"
                    ).all { header ->
                        csvParser.headerNames.any { it.equals(header, ignoreCase = true) }
                    }

                    if (!hasRequiredHeaders && movies.isEmpty()) {
                        errors.add("file non valido: headers letterboxd mancanti")
                        return@forEach
                    }

                    val title = getField(record, csvParser.headerNames, "Name", "name", "title")
                    val yearStr = getField(record, csvParser.headerNames, "Year", "year")
                    val ratingStr = getField(record, csvParser.headerNames, "Rating", "rating")
                    val watchedDate = getField(record, csvParser.headerNames, "Watched Date", "watched date", "date")

                    if (title.isNullOrBlank()) {
                        errors.add("riga ${record.recordNumber}: titolo vuoto")
                        return@forEach
                    }

                    val year = try {
                        yearStr?.toIntOrNull()?.let { y ->
                            if (y in 1888..2030) y else null
                        }
                    } catch (e: Exception) {
                        null
                    }

                    //letterboxd usa rating 0-5 stelle, converti a 0-10
                    val userRating = try {
                        ratingStr?.toDoubleOrNull()?.let { r ->
                            if (r in 0.0..5.0) r * 2.0 else null
                        }
                    } catch (e: Exception) {
                        null
                    }

                    val movie = Movie(
                        id = generateMovieId(title, year, DataSource.LETTERBOXD),
                        title = title.trim(),
                        year = year,
                        userRating = userRating,
                        dateRated = watchedDate?.trim(),
                        isWatched = true,
                        source = DataSource.LETTERBOXD
                    )

                    movies.add(movie)
                    successfulRows++

                    if (successfulRows <= 5) {
                        Log.d(TAG, "✓ parsed: ${movie.title} (${movie.year}) - rating: ${movie.userRating}")
                    }

                } catch (e: Exception) {
                    val errorMsg = "riga ${record.recordNumber}: ${e.message}"
                    errors.add(errorMsg)
                    Log.w(TAG, errorMsg)
                }
            }

            csvParser.close()
            reader.close()

            Log.d(TAG, "=== parsing completato ===")
            Log.d(TAG, "successo: $successfulRows film")
            Log.d(TAG, "errori: ${errors.size}")

            CsvParseResult(
                movies = movies,
                successfulRows = successfulRows,
                errors = errors,
                totalRows = successfulRows + errors.size
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ errore parsing letterboxd diary", e)
            CsvParseResult(
                movies = emptyList(),
                successfulRows = 0,
                errors = listOf("errore parsing: ${e.message}"),
                totalRows = 0
            )
        }
    }

    /**
     * parse letterboxd watchlist.csv
     */
    fun parseLetterboxdWatchlistCsv(inputStream: InputStream): CsvParseResult {
        Log.d(TAG, "=== parsing letterboxd watchlist.csv ===")

        return try {
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))

            val csvFormat = CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .setDelimiter(',')
                .build()

            val csvParser = CSVParser(reader, csvFormat)
            val movies = mutableListOf<Movie>()
            val errors = mutableListOf<String>()
            var successfulRows = 0

            Log.d(TAG, "headers rilevati: ${csvParser.headerNames.joinToString()}")

            csvParser.forEach { record ->
                try {
                    val hasRequiredHeaders = listOf(
                        "Name", "Year"
                    ).all { header ->
                        csvParser.headerNames.any { it.equals(header, ignoreCase = true) }
                    }

                    if (!hasRequiredHeaders && movies.isEmpty()) {
                        errors.add("file non valido: headers letterboxd watchlist mancanti")
                        return@forEach
                    }

                    val title = getField(record, csvParser.headerNames, "Name", "name", "title")
                    val yearStr = getField(record, csvParser.headerNames, "Year", "year")

                    if (title.isNullOrBlank()) {
                        errors.add("riga ${record.recordNumber}: titolo vuoto")
                        return@forEach
                    }

                    val year = try {
                        yearStr?.toIntOrNull()?.let { y ->
                            if (y in 1888..2030) y else null
                        }
                    } catch (e: Exception) {
                        null
                    }

                    val movie = Movie(
                        id = generateMovieId(title, year, DataSource.LETTERBOXD),
                        title = title.trim(),
                        year = year,
                        isWatched = false,
                        source = DataSource.LETTERBOXD
                    )

                    movies.add(movie)
                    successfulRows++

                    if (successfulRows <= 5) {
                        Log.d(TAG, "✓ parsed: ${movie.title} (${movie.year})")
                    }

                } catch (e: Exception) {
                    val errorMsg = "riga ${record.recordNumber}: ${e.message}"
                    errors.add(errorMsg)
                    Log.w(TAG, errorMsg)
                }
            }

            csvParser.close()
            reader.close()

            Log.d(TAG, "=== parsing completato ===")
            Log.d(TAG, "successo: $successfulRows film")
            Log.d(TAG, "errori: ${errors.size}")

            CsvParseResult(
                movies = movies,
                successfulRows = successfulRows,
                errors = errors,
                totalRows = successfulRows + errors.size
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ errore parsing letterboxd watchlist", e)
            CsvParseResult(
                movies = emptyList(),
                successfulRows = 0,
                errors = listOf("errore parsing: ${e.message}"),
                totalRows = 0
            )
        }
    }
    //HELPER FUNCTIONS

    /**
     * estrai campo da csv con fallback case-insensitive
     */
    private fun getField(
        record: org.apache.commons.csv.CSVRecord,
        headers: List<String>,
        vararg possibleNames: String
    ): String? {
        for (name in possibleNames) {
            //cerca header esatto
            if (headers.contains(name)) {
                return try {
                    record.get(name)?.trim()
                } catch (e: Exception) {
                    null
                }
            }

            //cerca case-insensitive
            val matchingHeader = headers.firstOrNull {
                it.equals(name, ignoreCase = true)
            }

            if (matchingHeader != null) {
                return try {
                    record.get(matchingHeader)?.trim()
                } catch (e: Exception) {
                    null
                }
            }
        }

        return null
    }

    /**
     * genera id univoco per film
     */
    private fun generateMovieId(title: String, year: Int?, source: DataSource): String {
        val normalizedTitle = title.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "")
            .replace(Regex("\\s+"), "_")
            .take(50)

        val yearStr = year?.toString() ?: "unknown"
        val sourcePrefix = when (source) {
            DataSource.IMDB -> "imdb"
            DataSource.LETTERBOXD -> "lbxd"
            else -> "unkn"
        }

        return "${sourcePrefix}_${normalizedTitle}_${yearStr}_${UUID.randomUUID().toString().take(8)}"
    }

    /**
     * auto-detect tipo csv
     */
    fun detectCsvType(inputStream: InputStream): CsvType {
        return try {
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val firstLine = reader.readLine()?.lowercase() ?: ""
            reader.close()

            when {
                firstLine.contains("your rating") && firstLine.contains("date rated") -> {
                    Log.d(TAG, "✓ rilevato: imdb ratings (watched)")
                    CsvType.IMDB_WATCHED
                }
                firstLine.contains("const") && firstLine.contains("title") && !firstLine.contains("rating") -> {
                    Log.d(TAG, "✓ rilevato: imdb watchlist")
                    CsvType.IMDB_WATCHLIST
                }
                firstLine.contains("watched date") || (firstLine.contains("name") && firstLine.contains("rating")) -> {
                    Log.d(TAG, "✓ rilevato: letterboxd diary (watched)")
                    CsvType.LETTERBOXD_WATCHED
                }
                firstLine.contains("name") && firstLine.contains("year") && !firstLine.contains("watched") -> {
                    Log.d(TAG, "✓ rilevato: letterboxd watchlist")
                    CsvType.LETTERBOXD_WATCHLIST
                }
                else -> {
                    Log.w(TAG, "⚠️ tipo csv sconosciuto")
                    Log.d(TAG, "headers: $firstLine")
                    CsvType.UNKNOWN
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "errore auto-detect", e)
            CsvType.UNKNOWN
        }
    }

    /**
     * validazione csv prima del parsing
     */
    fun validateCsv(inputStream: InputStream): CsvValidationResult {
        return try {
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
            val lines = mutableListOf<String>()

            //leggi prime 10 righe per validazione
            repeat(10) {
                val line = reader.readLine() ?: return@repeat
                lines.add(line)
            }
            reader.close()

            if (lines.isEmpty()) {
                return CsvValidationResult(
                    isValid = false,
                    error = "file vuoto",
                    detectedType = CsvType.UNKNOWN
                )
            }

            val headerLine = lines.firstOrNull() ?: ""
            val dataLines = lines.drop(1)

            //verifica delimitatori
            val commaCount = headerLine.count { it == ',' }
            val semicolonCount = headerLine.count { it == ';' }
            val tabCount = headerLine.count { it == '\t' }

            val delimiter = when {
                commaCount > semicolonCount && commaCount > tabCount -> ','
                semicolonCount > commaCount && semicolonCount > tabCount -> ';'
                tabCount > commaCount && tabCount > semicolonCount -> '\t'
                else -> ','
            }

            //verifica consistenza colonne
            val headerColumns = headerLine.split(delimiter).size
            val inconsistentRows = dataLines.count { line ->
                line.split(delimiter).size != headerColumns
            }

            if (inconsistentRows > dataLines.size / 2) {
                return CsvValidationResult(
                    isValid = false,
                    error = "struttura csv inconsistente",
                    detectedType = CsvType.UNKNOWN
                )
            }

            //rileva tipo
            val csvType = when {
                headerLine.contains("Your Rating", ignoreCase = true) -> CsvType.IMDB_WATCHED
                headerLine.contains("Const", ignoreCase = true) -> CsvType.IMDB_WATCHLIST
                headerLine.contains("Watched Date", ignoreCase = true) -> CsvType.LETTERBOXD_WATCHED
                headerLine.contains("Name", ignoreCase = true) &&
                        headerLine.contains("Year", ignoreCase = true) -> CsvType.LETTERBOXD_WATCHLIST
                else -> CsvType.UNKNOWN
            }

            CsvValidationResult(
                isValid = true,
                error = null,
                detectedType = csvType,
                delimiter = delimiter,
                columnCount = headerColumns,
                estimatedRows = dataLines.size
            )

        } catch (e: Exception) {
            Log.e(TAG, "errore validazione csv", e)
            CsvValidationResult(
                isValid = false,
                error = "errore validazione: ${e.message}",
                detectedType = CsvType.UNKNOWN
            )
        }
    }

    /**
     * statistiche parsing
     */
    fun getParsingStats(result: CsvParseResult): String {
        return buildString {
            appendLine("=== statistiche parsing ===")
            appendLine()
            appendLine("film importati: ${result.movies.size}")
            appendLine("righe processate: ${result.successfulRows}")
            appendLine("righe totali: ${result.totalRows}")

            if (result.errors.isNotEmpty()) {
                appendLine("errori: ${result.errors.size}")
                appendLine()
                appendLine("primi errori:")
                result.errors.take(5).forEach { error ->
                    appendLine("• $error")
                }
            }

            if (result.movies.isNotEmpty()) {
                val withYear = result.movies.count { it.year != null }
                val withRating = result.movies.count { it.userRating != null }
                val watched = result.movies.count { it.isWatched }

                appendLine()
                appendLine("dettagli:")
                appendLine("• con anno: $withYear")
                appendLine("• con rating: $withRating")
                appendLine("• visti: $watched")
                appendLine("• da vedere: ${result.movies.size - watched}")
            }
        }
    }
}

//data classes

data class CsvParseResult(
    val movies: List<Movie>,
    val successfulRows: Int,
    val errors: List<String>,
    val totalRows: Int
) {
    val successRate: Double
        get() = if (totalRows > 0) (successfulRows.toDouble() / totalRows) * 100 else 0.0
}

data class CsvValidationResult(
    val isValid: Boolean,
    val error: String?,
    val detectedType: CsvType,
    val delimiter: Char = ',',
    val columnCount: Int = 0,
    val estimatedRows: Int = 0
)

enum class CsvType {
    IMDB_WATCHED,
    IMDB_WATCHLIST,
    LETTERBOXD_WATCHED,
    LETTERBOXD_WATCHLIST,
    UNKNOWN
}