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
 */
class CsvProcessor {

    private val TAG = "CsvProcessor"

    /**
     * parse imdb watched.csv (watched list)
     */
    fun parseImdbWatchedCsv(inputStream: InputStream): CsvParseResult {
        Log.d(TAG, "=== parsing imdb ratings.csv ===")

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
                        "Title", "Year", "Your Rating", "Date Rated"
                    ).all { header ->
                        csvParser.headerNames.any { it.equals(header, ignoreCase = true) }
                    }

                    if (!hasRequiredHeaders && movies.isEmpty()) {
                        errors.add("file non valido: headers imdb mancanti")
                        return@forEach
                    }

                    val title = getField(record, csvParser.headerNames, "Title", "title")
                    val yearStr = getField(record, csvParser.headerNames, "Year", "year")
                    val ratingStr = getField(record, csvParser.headerNames, "Your Rating", "your rating", "rating")
                    val dateRated = getField(record, csvParser.headerNames, "Date Rated", "date rated", "date")
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

                    val rating = try {
                        ratingStr?.toDoubleOrNull()?.let { r ->
                            if (r in 0.0..10.0) r else null
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
                        userRating = rating,
                        dateRated = dateRated,
                        isWatched = true,
                        source = DataSource.IMDB
                    )

                    movies.add(movie)
                    successfulRows++

                    if (successfulRows <= 5) {
                        Log.d(TAG, "parsed: ${movie.title} (${movie.year}) - VISTO")
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
            Log.d(TAG, "successo: $successfulRows film VISTI")
            Log.d(TAG, "errori: ${errors.size}")

            CsvParseResult(
                movies = movies,
                successfulRows = successfulRows,
                errors = errors,
                totalRows = successfulRows + errors.size
            )

        } catch (e: Exception) {
            Log.e(TAG, "errore parsing imdb ratings", e)
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
                        Log.d(TAG, "parsed: ${movie.title} (${movie.year}) - DA VEDERE")
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
            Log.d(TAG, "successo: $successfulRows film DA VEDERE")
            Log.d(TAG, "errori: ${errors.size}")

            CsvParseResult(
                movies = movies,
                successfulRows = successfulRows,
                errors = errors,
                totalRows = successfulRows + errors.size
            )

        } catch (e: Exception) {
            Log.e(TAG, "errore parsing imdb watchlist", e)
            CsvParseResult(
                movies = emptyList(),
                successfulRows = 0,
                errors = listOf("errore parsing: ${e.message}"),
                totalRows = 0
            )
        }
    }

    /**
     * parse letterboxd watched.csv (watched)
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
                    val name = getField(record, csvParser.headerNames, "Name", "name", "title")
                    val yearStr = getField(record, csvParser.headerNames, "Year", "year")
                    val ratingStr = getField(record, csvParser.headerNames, "Rating", "rating")
                    val watchedDate = getField(record, csvParser.headerNames, "Watched Date", "watched date", "date")

                    if (name.isNullOrBlank()) {
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

                    val rating = try {
                        ratingStr?.toDoubleOrNull()?.let { r ->
                            (r * 2).coerceIn(0.0, 10.0)
                        }
                    } catch (e: Exception) {
                        null
                    }

                    val movie = Movie(
                        id = generateMovieId(name, year, DataSource.LETTERBOXD),
                        title = name.trim(),
                        year = year,
                        userRating = rating,
                        dateRated = watchedDate,
                        isWatched = true,
                        source = DataSource.LETTERBOXD
                    )

                    movies.add(movie)
                    successfulRows++

                    if (successfulRows <= 5) {
                        Log.d(TAG, "parsed: ${movie.title} (${movie.year}) - VISTO")
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
            Log.d(TAG, "successo: $successfulRows film VISTI")
            Log.d(TAG, "errori: ${errors.size}")

            CsvParseResult(
                movies = movies,
                successfulRows = successfulRows,
                errors = errors,
                totalRows = successfulRows + errors.size
            )

        } catch (e: Exception) {
            Log.e(TAG, "errore parsing letterboxd diary", e)
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
                    val name = getField(record, csvParser.headerNames, "Name", "name", "title")
                    val yearStr = getField(record, csvParser.headerNames, "Year", "year")

                    if (name.isNullOrBlank()) {
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
                        id = generateMovieId(name, year, DataSource.LETTERBOXD),
                        title = name.trim(),
                        year = year,
                        isWatched = false,
                        source = DataSource.LETTERBOXD
                    )

                    movies.add(movie)
                    successfulRows++

                    if (successfulRows <= 5) {
                        Log.d(TAG, "parsed: ${movie.title} (${movie.year}) - DA VEDERE")
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
            Log.d(TAG, "successo: $successfulRows film DA VEDERE")
            Log.d(TAG, "errori: ${errors.size}")

            CsvParseResult(
                movies = movies,
                successfulRows = successfulRows,
                errors = errors,
                totalRows = successfulRows + errors.size
            )

        } catch (e: Exception) {
            Log.e(TAG, "errore parsing letterboxd watchlist", e)
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
}

//data classes

data class CsvParseResult(
    val movies: List<Movie>,
    val successfulRows: Int,
    val errors: List<String>,
    val totalRows: Int
)