"use strict";
var __decorate = (this && this.__decorate) || function (decorators, target, key, desc) {
    var c = arguments.length, r = c < 3 ? target : desc === null ? desc = Object.getOwnPropertyDescriptor(target, key) : desc, d;
    if (typeof Reflect === "object" && typeof Reflect.decorate === "function") r = Reflect.decorate(decorators, target, key, desc);
    else for (var i = decorators.length - 1; i >= 0; i--) if (d = decorators[i]) r = (c < 3 ? d(r) : c > 3 ? d(target, key, r) : d(target, key)) || r;
    return c > 3 && r && Object.defineProperty(target, key, r), r;
};
var __metadata = (this && this.__metadata) || function (k, v) {
    if (typeof Reflect === "object" && typeof Reflect.metadata === "function") return Reflect.metadata(k, v);
};
var MoviesService_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.MoviesService = void 0;
const common_1 = require("@nestjs/common");
const database_service_1 = require("../../database/database.service");
const tmdb_service_1 = require("../tmdb/tmdb.service");
const websocket_gateway_1 = require("../websocket/websocket.gateway");
const user_movies_service_1 = require("./user-movies.service");
const user_movie_entity_1 = require("../../database/entities/user-movie.entity");
const uuid_1 = require("uuid");
let MoviesService = MoviesService_1 = class MoviesService {
    constructor(databaseService, tmdbService, websocketGateway, userMoviesService) {
        this.databaseService = databaseService;
        this.tmdbService = tmdbService;
        this.websocketGateway = websocketGateway;
        this.userMoviesService = userMoviesService;
        this.logger = new common_1.Logger(MoviesService_1.name);
    }
    async batchUploadWithUserAssociation(userId, watchlist, watched) {
        const sessionId = (0, uuid_1.v4)();
        const startTime = Date.now();
        this.logger.log(`📦 === BATCH UPLOAD SESSION ${sessionId} ===`);
        this.logger.log(`User ID: ${userId}`);
        this.logger.log(`watchlist: ${watchlist.length} film`);
        this.logger.log(`watched: ${watched.length} film`);
        try {
            this.logger.log(`💾 salvataggio film in tabella movies...`);
            const allMovies = [...watchlist, ...watched];
            const moviesWithoutUserId = allMovies.map(movie => {
                const { user_id, ...movieWithoutUser } = movie;
                return movieWithoutUser;
            });
            await this.databaseService.saveMovies(moviesWithoutUserId);
            this.logger.log(`✅ salvati ${moviesWithoutUserId.length} film in tabella movies`);
            this.logger.log(`🎬 enrichment watchlist...`);
            const watchlistResult = await this.enrichMovies(watchlist);
            this.logger.log(`🎬 enrichment watched...`);
            const watchedResult = await this.enrichMovies(watched);
            this.logger.log(`🔗 associazione film all'utente nella tabella user_movies...`);
            const watchlistAssociations = watchlistResult.successfulMovies.map((movie) => ({
                movieId: movie.id,
                status: user_movie_entity_1.MovieStatus.WATCHLIST,
                source: movie.source || 'UNKNOWN',
                userRating: movie.user_rating,
                watchedDate: undefined,
                userReview: movie.user_review,
            }));
            const watchedAssociations = watchedResult.successfulMovies.map((movie) => ({
                movieId: movie.id,
                status: user_movie_entity_1.MovieStatus.WATCHED,
                source: movie.source || 'UNKNOWN',
                userRating: movie.user_rating,
                watchedDate: movie.watched_date ? new Date(movie.watched_date) : undefined,
                userReview: movie.user_review,
            }));
            const watchlistBatchResult = await this.userMoviesService.batchAssociateMoviesToUser(userId, watchlistAssociations);
            const watchedBatchResult = await this.userMoviesService.batchAssociateMoviesToUser(userId, watchedAssociations);
            this.logger.log(`✅ associazioni completate:`);
            this.logger.log(`   watchlist: ${watchlistBatchResult.created} creati, ${watchlistBatchResult.updated} aggiornati`);
            this.logger.log(`   watched: ${watchedBatchResult.created} creati, ${watchedBatchResult.updated} aggiornati`);
            const userStats = await this.userMoviesService.getUserMovieStats(userId);
            const importCounters = {
                watchedFromFile: watched.length,
                watchlistFromFile: watchlist.length,
                totalWatched: userStats.watchedCount,
                totalWatchlist: userStats.watchlistCount,
            };
            this.logger.log(`📊 Contatori importazione:`);
            this.logger.log(`   Dal file: ${importCounters.watchedFromFile} watched, ${importCounters.watchlistFromFile} watchlist`);
            this.logger.log(`   Totali: ${importCounters.totalWatched} watched, ${importCounters.totalWatchlist} watchlist`);
            const totalMovies = watchlist.length + watched.length;
            const totalEnriched = watchlistResult.successfulMovies.filter((m) => m.tmdb_id).length +
                watchedResult.successfulMovies.filter((m) => m.tmdb_id).length;
            const totalCacheHits = watchlistResult.cacheHits + watchedResult.cacheHits;
            const duration = Date.now() - startTime;
            this.logger.log(`=== BATCH UPLOAD COMPLETATO ===`);
            this.logger.log(`durata: ${(duration / 1000).toFixed(1)}s`);
            this.logger.log(`film totali: ${totalMovies}`);
            this.logger.log(`film arricchiti: ${totalEnriched}`);
            this.logger.log(`cache hits: ${totalCacheHits}`);
            this.logger.log(`success rate: ${((totalEnriched / totalMovies) * 100).toFixed(1)}%`);
            const result = {
                sessionId,
                watchlistResult,
                watchedResult,
                importCounters,
                summary: {
                    totalMovies,
                    watchlistCount: watchlist.length,
                    watchedCount: watched.length,
                    totalEnriched,
                    overallSuccessRate: totalEnriched / totalMovies,
                    cacheHitsTotal: totalCacheHits,
                },
            };
            return result;
        }
        catch (error) {
            this.logger.error(`❌ errore batch upload session ${sessionId}: ${error.message}`);
            throw error;
        }
    }
    async healthCheck() {
        return {
            status: 'ok',
            timestamp: new Date().toISOString(),
        };
    }
    async getAllMovies() {
        try {
            return await this.databaseService.getAllMovies();
        }
        catch (error) {
            this.logger.error(`errore getAllMovies: ${error.message}`);
            return [];
        }
    }
    async getUnenrichedMovies() {
        try {
            return await this.databaseService.getUnenrichedMovies();
        }
        catch (error) {
            this.logger.error(`errore getUnenrichedMovies: ${error.message}`);
            return [];
        }
    }
    async deleteAllMovies() {
        try {
            await this.databaseService.deleteAllMovies();
            this.logger.log('🗑️ tutti i film eliminati');
        }
        catch (error) {
            this.logger.error(`errore deleteAllMovies: ${error.message}`);
            throw error;
        }
    }
    async getUserMovies(userId, filters) {
        try {
            this.logger.debug(`🔍 Recupero film per utente ${userId}`);
            const movieStatus = filters?.status === 'watched'
                ? user_movie_entity_1.MovieStatus.WATCHED
                : filters?.status === 'watchlist'
                    ? user_movie_entity_1.MovieStatus.WATCHLIST
                    : undefined;
            const userMovieIds = await this.userMoviesService.getUserMovieIds(userId, movieStatus);
            if (userMovieIds.length === 0) {
                this.logger.debug(`ℹ️ Nessun film trovato per utente ${userId}`);
                return { movies: [], total: 0 };
            }
            let movies = await this.databaseService.getMoviesByIds(userMovieIds);
            if (filters?.query) {
                const query = filters.query.toLowerCase();
                movies = movies.filter((m) => m.title.toLowerCase().includes(query) ||
                    m.director?.toLowerCase().includes(query) ||
                    m.overview?.toLowerCase().includes(query));
            }
            if (filters?.genre) {
                movies = movies.filter((m) => m.genres?.some((g) => g.toLowerCase() === filters.genre.toLowerCase()));
            }
            if (filters?.year) {
                movies = movies.filter((m) => m.year === filters.year);
            }
            if (filters?.director) {
                const director = filters.director.toLowerCase();
                movies = movies.filter((m) => m.director?.toLowerCase().includes(director));
            }
            if (filters?.minRating) {
                movies = movies.filter((m) => (m.user_rating && m.user_rating >= filters.minRating) ||
                    (m.tmdb_rating && m.tmdb_rating >= filters.minRating));
            }
            if (filters?.maxRating) {
                movies = movies.filter((m) => (m.user_rating && m.user_rating <= filters.maxRating) ||
                    (m.tmdb_rating && m.tmdb_rating <= filters.maxRating));
            }
            const total = movies.length;
            if (filters?.sortBy) {
                movies = this.sortMovies(movies, filters.sortBy, filters.sortOrder);
            }
            const offset = filters?.offset || 0;
            const limit = filters?.limit || 50;
            movies = movies.slice(offset, offset + limit);
            this.logger.debug(`🔍 ricerca: trovati ${total} film per utente ${userId} (mostrati ${movies.length})`);
            return { movies, total };
        }
        catch (error) {
            this.logger.error(`errore recupero film utente: ${error.message}`);
            return { movies: [], total: 0 };
        }
    }
    async getUserStats(userId) {
        try {
            const userStats = await this.userMoviesService.getUserMovieStats(userId);
            this.logger.debug(`📊 statistiche utente ${userId}:`, userStats);
            return {
                user_id: userId,
                total_movies: userStats.totalMovies,
                watched_count: userStats.watchedCount,
                watchlist_count: userStats.watchlistCount,
                average_rating: userStats.averageRating,
                last_import: userStats.lastImportDate,
            };
        }
        catch (error) {
            this.logger.error(`errore recupero statistiche utente: ${error.message}`);
            return { error: error.message };
        }
    }
    async enrichMovies(movies) {
        const startTime = Date.now();
        const successfulMovies = [];
        const failedMovies = [];
        let cacheHits = 0;
        let tmdbCalls = 0;
        this.logger.debug(`🎬 enrichMovies: inizio arricchimento ${movies.length} film`);
        for (const movie of movies) {
            try {
                if (movie.tmdb_id) {
                    const cached = movie.tmdb_id
                        ? await this.databaseService.getMovieByTmdbId(movie.tmdb_id)
                        : null;
                    if (cached) {
                        cacheHits++;
                        this.logger.debug(`💰 cache hit per ${movie.title}`);
                        successfulMovies.push({ ...movie, ...cached });
                        continue;
                    }
                }
                const tmdbData = await this.tmdbService.enrichMovie(movie);
                tmdbCalls++;
                if (tmdbData) {
                    this.logger.debug(`✅ trovato su TMDB: ${movie.title}`);
                    const enriched = { ...movie, ...tmdbData };
                    successfulMovies.push(enriched);
                }
                else {
                    this.logger.warn(`❌ non trovato su TMDB: ${movie.title}`);
                    failedMovies.push(movie);
                }
            }
            catch (error) {
                this.logger.error(`errore arricchimento ${movie.title}: ${error.message}`);
                failedMovies.push(movie);
            }
        }
        const duration = Date.now() - startTime;
        const successRate = successfulMovies.length / movies.length;
        this.logger.log(`✅ enrichMovies completato:`);
        this.logger.log(`   successi: ${successfulMovies.length}`);
        this.logger.log(`   falliti: ${failedMovies.length}`);
        this.logger.log(`   cache hits: ${cacheHits}`);
        this.logger.log(`   TMDB calls: ${tmdbCalls}`);
        this.logger.log(`   success rate: ${(successRate * 100).toFixed(1)}%`);
        this.logger.log(`   durata: ${(duration / 1000).toFixed(1)}s`);
        const sessionId = `enrich_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
        const totalProcessed = successfulMovies.length + failedMovies.length;
        return {
            sessionId,
            successfulMovies,
            failedMovies: failedMovies.map((movie) => ({
                movie: {
                    id: movie.id,
                    title: movie.title,
                    year: movie.year,
                    user_rating: movie.user_rating,
                    watched_date: movie.watched_date,
                    user_review: movie.user_review,
                    is_watched: movie.is_watched,
                    source: movie.source,
                },
                error: 'Enrichment failed',
            })),
            totalProcessed,
            successRate,
            cacheHits,
        };
    }
    sortMovies(movies, sortBy, sortOrder = 'ASC') {
        const sorted = [...movies].sort((a, b) => {
            let comparison = 0;
            switch (sortBy) {
                case 'title':
                    comparison = a.title.localeCompare(b.title);
                    break;
                case 'year':
                    comparison = (a.year || 0) - (b.year || 0);
                    break;
                case 'rating':
                    const ratingA = a.user_rating || a.tmdb_rating || 0;
                    const ratingB = b.user_rating || b.tmdb_rating || 0;
                    comparison = ratingA - ratingB;
                    break;
                case 'popularity':
                    comparison = (a.popularity || 0) - (b.popularity || 0);
                    break;
                default:
                    comparison = 0;
            }
            return sortOrder === 'ASC' ? comparison : -comparison;
        });
        return sorted;
    }
    async batchUpload(watchlist, watched) {
        const sessionId = (0, uuid_1.v4)();
        const startTime = Date.now();
        this.logger.log(`📦 === BATCH UPLOAD SESSION ${sessionId} ===`);
        this.logger.log(`watchlist: ${watchlist.length} film`);
        this.logger.log(`watched: ${watched.length} film`);
        try {
            this.logger.log(`💾 salvataggio film in database...`);
            const allMovies = [...watchlist, ...watched];
            await this.databaseService.saveMovies(allMovies);
            this.logger.log(`✅ salvati ${allMovies.length} film`);
            this.logger.log(`🎬 enrichment watchlist...`);
            const watchlistResult = await this.enrichMovies(watchlist);
            this.logger.log(`🎬 enrichment watched...`);
            const watchedResult = await this.enrichMovies(watched);
            const totalMovies = watchlist.length + watched.length;
            const totalEnriched = watchlistResult.successfulMovies.filter((m) => m.tmdb_id).length +
                watchedResult.successfulMovies.filter((m) => m.tmdb_id).length;
            const totalCacheHits = watchlistResult.cacheHits + watchedResult.cacheHits;
            const duration = Date.now() - startTime;
            this.logger.log(`=== BATCH UPLOAD COMPLETATO ===`);
            this.logger.log(`durata: ${(duration / 1000).toFixed(1)}s`);
            this.logger.log(`film totali: ${totalMovies}`);
            this.logger.log(`film arricchiti: ${totalEnriched}`);
            this.logger.log(`cache hits: ${totalCacheHits}`);
            this.logger.log(`success rate: ${((totalEnriched / totalMovies) * 100).toFixed(1)}%`);
            return {
                sessionId,
                watchlistResult,
                watchedResult,
                summary: {
                    totalMovies,
                    watchlistCount: watchlist.length,
                    watchedCount: watched.length,
                    totalEnriched,
                    overallSuccessRate: totalEnriched / totalMovies,
                    cacheHitsTotal: totalCacheHits,
                },
            };
        }
        catch (error) {
            this.logger.error(`❌ errore batch upload session ${sessionId}: ${error.message}`);
            throw error;
        }
    }
    async searchMovies(filters) {
        try {
            const query = filters.query || '';
            this.logger.debug(`🔍 ricerca film: "${query}"`);
            let movies = await this.databaseService.getAllMovies();
            if (query) {
                const lowerQuery = query.toLowerCase();
                movies = movies.filter((m) => m.title.toLowerCase().includes(lowerQuery) ||
                    m.director?.toLowerCase().includes(lowerQuery) ||
                    m.overview?.toLowerCase().includes(lowerQuery));
            }
            if (filters?.genre) {
                movies = movies.filter((m) => m.genres?.some((g) => g.toLowerCase() === filters.genre.toLowerCase()));
            }
            if (filters?.year) {
                movies = movies.filter((m) => m.year === filters.year);
            }
            if (filters?.director) {
                const director = filters.director.toLowerCase();
                movies = movies.filter((m) => m.director?.toLowerCase().includes(director));
            }
            if (filters?.minRating) {
                movies = movies.filter((m) => (m.user_rating && m.user_rating >= filters.minRating) ||
                    (m.tmdb_rating && m.tmdb_rating >= filters.minRating));
            }
            if (filters?.maxRating) {
                movies = movies.filter((m) => (m.user_rating && m.user_rating <= filters.maxRating) ||
                    (m.tmdb_rating && m.tmdb_rating <= filters.maxRating));
            }
            const total = movies.length;
            if (filters?.sortBy) {
                movies = this.sortMovies(movies, filters.sortBy, filters.sortOrder);
            }
            const offset = filters?.offset || 0;
            const limit = filters?.limit || 50;
            movies = movies.slice(offset, offset + limit);
            this.logger.debug(`🔍 ricerca: trovati ${total} film (mostrati ${movies.length})`);
            return { movies, total };
        }
        catch (error) {
            this.logger.error(`errore ricerca film: ${error.message}`);
            return { movies: [], total: 0 };
        }
    }
    async getMovieById(id) {
        try {
            return await this.databaseService.getMovieById(id);
        }
        catch (error) {
            this.logger.error(`errore recupero film ${id}: ${error.message}`);
            return null;
        }
    }
    async updateMovie(id, updates) {
        try {
            this.logger.debug(`🔄 aggiornamento film ${id}`);
            const updated = await this.databaseService.updateMovie(id, updates);
            this.logger.debug(`✅ film ${id} aggiornato`);
            return updated;
        }
        catch (error) {
            this.logger.error(`errore aggiornamento film ${id}: ${error.message}`);
            throw error;
        }
    }
    async deleteMovie(id) {
        try {
            this.logger.debug(`🗑️ eliminazione film ${id}`);
            await this.databaseService.deleteMovie(id);
            this.logger.debug(`✅ film ${id} eliminato`);
        }
        catch (error) {
            this.logger.error(`errore eliminazione film ${id}: ${error.message}`);
            throw error;
        }
    }
    async getStats() {
        try {
            const movies = await this.databaseService.getAllMovies();
            const watchlistCount = movies.filter((m) => m.status === 'watchlist').length;
            const watchedCount = movies.filter((m) => m.status === 'watched').length;
            const totalRatings = movies.reduce((sum, m) => sum + (m.user_rating || 0), 0);
            const ratedMovies = movies.filter((m) => m.user_rating).length;
            const averageRating = ratedMovies > 0 ? totalRatings / ratedMovies : 0;
            return {
                total_movies: movies.length,
                watchlist_count: watchlistCount,
                watched_count: watchedCount,
                average_rating: averageRating,
                genres: this.extractGenres(movies),
            };
        }
        catch (error) {
            this.logger.error(`errore recupero statistiche: ${error.message}`);
            return { error: error.message };
        }
    }
    extractGenres(movies) {
        const genres = new Set();
        movies.forEach((m) => {
            if (m.genres) {
                m.genres.forEach((g) => genres.add(g));
            }
        });
        return Array.from(genres).sort();
    }
};
exports.MoviesService = MoviesService;
exports.MoviesService = MoviesService = MoviesService_1 = __decorate([
    (0, common_1.Injectable)(),
    __metadata("design:paramtypes", [database_service_1.DatabaseService,
        tmdb_service_1.TmdbService,
        websocket_gateway_1.WebsocketGateway,
        user_movies_service_1.UserMoviesService])
], MoviesService);
//# sourceMappingURL=movies.service.js.map