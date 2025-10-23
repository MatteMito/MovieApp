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
const uuid_1 = require("uuid");
let MoviesService = MoviesService_1 = class MoviesService {
    constructor(databaseService, tmdbService, websocketGateway) {
        this.databaseService = databaseService;
        this.tmdbService = tmdbService;
        this.websocketGateway = websocketGateway;
        this.logger = new common_1.Logger(MoviesService_1.name);
    }
    async enrichMovies(movies) {
        const sessionId = (0, uuid_1.v4)();
        const startTime = new Date().toISOString();
        this.logger.log(`🎬 === ENRICHMENT SESSION ${sessionId} ===`);
        this.logger.log(`film da processare: ${movies.length}`);
        const result = {
            sessionId,
            successfulMovies: [],
            failedMovies: [],
            totalProcessed: 0,
            successRate: 0,
            cacheHits: 0,
        };
        let lastReportedProgress = 0;
        try {
            const alreadyEnriched = [];
            const needEnrichment = [];
            for (const movie of movies) {
                const existing = await this.databaseService.findMovieByTitleYear(movie.title, movie.year);
                if (existing && existing.tmdb_id) {
                    this.logger.debug(`✅ già arricchito in db: ${movie.title}`);
                    const merged = {
                        ...existing,
                        id: movie.id,
                        user_rating: movie.user_rating,
                        watched_date: movie.watched_date,
                        user_review: movie.user_review,
                        is_watched: movie.is_watched,
                        source: movie.source,
                    };
                    alreadyEnriched.push(merged);
                    result.cacheHits++;
                }
                else {
                    needEnrichment.push(movie);
                }
            }
            this.logger.log(`📊 analisi iniziale:`);
            this.logger.log(`   già arricchiti in db: ${alreadyEnriched.length}`);
            this.logger.log(`   da arricchire: ${needEnrichment.length}`);
            result.successfulMovies.push(...alreadyEnriched);
            lastReportedProgress = alreadyEnriched.length;
            await this.websocketGateway.notifyEnrichmentProgress(sessionId, lastReportedProgress, movies.length, undefined);
            if (needEnrichment.length > 0) {
                this.logger.log(`🔍 avvio enrichment tmdb per ${needEnrichment.length} film`);
                const enrichmentResult = await this.tmdbService.enrichMovies(needEnrichment, {
                    onProgress: async (processed, total, currentMovie) => {
                        const calculatedProgress = alreadyEnriched.length + processed;
                        const cappedProgress = Math.min(calculatedProgress, movies.length);
                        const monotonicProgress = Math.max(cappedProgress, lastReportedProgress);
                        if (monotonicProgress > lastReportedProgress) {
                            lastReportedProgress = monotonicProgress;
                            this.logger.debug(`📊 progress: ${monotonicProgress}/${movies.length} (${currentMovie})`);
                            await this.websocketGateway.notifyEnrichmentProgress(sessionId, monotonicProgress, movies.length, currentMovie);
                        }
                        else {
                            this.logger.debug(`⏭️  skip progress update: calculated=${calculatedProgress}, capped=${cappedProgress}, last=${lastReportedProgress}`);
                        }
                    },
                });
                if (enrichmentResult.successfulMovies.length > 0) {
                    this.logger.log(`💾 salvataggio ${enrichmentResult.successfulMovies.length} film arricchiti`);
                    const savedMovies = await this.databaseService.saveMovies(enrichmentResult.successfulMovies);
                    result.successfulMovies.push(...savedMovies);
                }
                result.failedMovies.push(...enrichmentResult.failedMovies);
            }
            result.totalProcessed = movies.length;
            result.successRate = result.successfulMovies.length / result.totalProcessed;
            const enrichedCount = result.successfulMovies.filter((m) => m.tmdb_id).length;
            this.logger.log(`=== ENRICHMENT COMPLETATO ===`);
            this.logger.log(`session id: ${sessionId}`);
            this.logger.log(`film processati: ${result.totalProcessed}`);
            this.logger.log(`film arricchiti: ${enrichedCount}`);
            this.logger.log(`cache hits: ${result.cacheHits}`);
            this.logger.log(`nuovi da tmdb: ${enrichedCount - result.cacheHits}`);
            this.logger.log(`falliti: ${result.failedMovies.length}`);
            this.logger.log(`success rate: ${(result.successRate * 100).toFixed(1)}%`);
            await this.websocketGateway.notifyEnrichmentCompleted(sessionId, result.totalProcessed, result.successfulMovies.length, result.cacheHits);
            return result;
        }
        catch (error) {
            this.logger.error(`❌ errore enrichment session ${sessionId}: ${error.message}`);
            await this.websocketGateway.notifyEnrichmentError(sessionId, error.message);
            throw error;
        }
    }
    async batchUpload(watchlist, watched) {
        const sessionId = (0, uuid_1.v4)();
        const startTime = Date.now();
        this.logger.log(`📦 === BATCH UPLOAD SESSION ${sessionId} ===`);
        this.logger.log(`watchlist: ${watchlist.length} film`);
        this.logger.log(`watched: ${watched.length} film`);
        try {
            this.logger.log(`💾 salvataggio iniziale film grezzi...`);
            const allMovies = [...watchlist, ...watched];
            await this.databaseService.saveMovies(allMovies);
            this.logger.log(`✅ salvati ${allMovies.length} film in database`);
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
            const result = {
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
            await this.websocketGateway.notifyBatchCompleted(sessionId, watchlist.length, watched.length, totalMovies);
            return result;
        }
        catch (error) {
            this.logger.error(`❌ errore batch upload ${sessionId}: ${error.message}`);
            throw error;
        }
    }
    async getAllMovies() {
        try {
            const movies = await this.databaseService.getAllMovies();
            const enrichedCount = movies.filter((m) => m.tmdb_id).length;
            this.logger.log(`📚 recuperati ${movies.length} film (${enrichedCount} arricchiti)`);
            return movies;
        }
        catch (error) {
            this.logger.error(`errore recupero film: ${error.message}`);
            throw error;
        }
    }
    async getMovieById(id) {
        try {
            const movie = await this.databaseService.getMovieById(id);
            if (!movie) {
                this.logger.debug(`film non trovato: ${id}`);
                return null;
            }
            this.logger.debug(`film recuperato: ${movie.title} (arricchito: ${!!movie.tmdb_id})`);
            return movie;
        }
        catch (error) {
            this.logger.error(`errore recupero film ${id}: ${error.message}`);
            return null;
        }
    }
    async getUnenrichedMovies() {
        try {
            const movies = await this.databaseService.getUnenrichedMovies();
            this.logger.log(`📊 film non arricchiti: ${movies.length}`);
            return movies;
        }
        catch (error) {
            this.logger.error(`errore recupero film non arricchiti: ${error.message}`);
            return [];
        }
    }
    async updateMovie(id, updates) {
        try {
            const existing = await this.databaseService.getMovieById(id);
            if (!existing) {
                throw new Error(`film non trovato: ${id}`);
            }
            const updated = {
                ...existing,
                ...updates,
                id,
            };
            const savedMovie = await this.databaseService.saveMovie(updated);
            this.logger.log(`✅ film aggiornato: ${savedMovie.title}`);
            return savedMovie;
        }
        catch (error) {
            this.logger.error(`errore aggiornamento film ${id}: ${error.message}`);
            throw error;
        }
    }
    async deleteMovie(id) {
        try {
            const movie = await this.databaseService.getMovieById(id);
            if (!movie) {
                throw new Error(`film non trovato: ${id}`);
            }
            this.logger.log(`🗑️ film eliminato: ${movie.title}`);
        }
        catch (error) {
            this.logger.error(`errore eliminazione film ${id}: ${error.message}`);
            throw error;
        }
    }
    async deleteAllMovies() {
        try {
            await this.databaseService.deleteAllMovies();
            this.logger.log(`🗑️ database svuotato`);
        }
        catch (error) {
            this.logger.error(`errore eliminazione film: ${error.message}`);
            throw error;
        }
    }
    async searchMovies(filters) {
        try {
            let movies = await this.databaseService.getAllMovies();
            if (filters.query) {
                const query = filters.query.toLowerCase();
                movies = movies.filter((m) => m.title.toLowerCase().includes(query) ||
                    m.director?.toLowerCase().includes(query) ||
                    m.overview?.toLowerCase().includes(query));
            }
            if (filters.genre) {
                movies = movies.filter((m) => m.genres?.some((g) => g.toLowerCase() === filters.genre.toLowerCase()));
            }
            if (filters.year) {
                movies = movies.filter((m) => m.year === filters.year);
            }
            if (filters.director) {
                const director = filters.director.toLowerCase();
                movies = movies.filter((m) => m.director?.toLowerCase().includes(director));
            }
            if (filters.minRating) {
                movies = movies.filter((m) => (m.user_rating && m.user_rating >= filters.minRating) ||
                    (m.tmdb_rating && m.tmdb_rating >= filters.minRating));
            }
            if (filters.maxRating) {
                movies = movies.filter((m) => (m.user_rating && m.user_rating <= filters.maxRating) ||
                    (m.tmdb_rating && m.tmdb_rating <= filters.maxRating));
            }
            if (filters.watched !== undefined) {
                movies = movies.filter((m) => m.is_watched === filters.watched);
            }
            const total = movies.length;
            if (filters.sortBy) {
                movies = this.sortMovies(movies, filters.sortBy, filters.sortOrder);
            }
            const offset = filters.offset || 0;
            const limit = filters.limit || 50;
            movies = movies.slice(offset, offset + limit);
            this.logger.debug(`🔍 ricerca: trovati ${total} film (mostrati ${movies.length})`);
            return { movies, total };
        }
        catch (error) {
            this.logger.error(`errore ricerca film: ${error.message}`);
            return { movies: [], total: 0 };
        }
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
    async getStats() {
        try {
            const dbStats = await this.databaseService.getStats();
            this.logger.debug(`📊 statistiche recuperate`);
            return dbStats;
        }
        catch (error) {
            this.logger.error(`errore recupero statistiche: ${error.message}`);
            return { error: error.message };
        }
    }
    async healthCheck() {
        try {
            const [dbHealth, tmdbHealth] = await Promise.all([
                this.databaseService.healthCheck(),
                this.tmdbService.healthCheck(),
            ]);
            const dbStats = await this.databaseService.getStats();
            return {
                status: 'healthy',
                components: {
                    database: dbHealth,
                    tmdb: tmdbHealth,
                    websocket: this.websocketGateway.getConnectionInfo(),
                },
                statistics: dbStats,
                features: {
                    intelligentEnrichment: 'enabled with is_enriched flag',
                    autoSync: 'enabled',
                    cacheOptimization: 'active',
                    websocketNotifications: 'active',
                },
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            return {
                status: 'degraded',
                error: error.message,
                timestamp: new Date().toISOString(),
            };
        }
    }
};
exports.MoviesService = MoviesService;
exports.MoviesService = MoviesService = MoviesService_1 = __decorate([
    (0, common_1.Injectable)(),
    __metadata("design:paramtypes", [database_service_1.DatabaseService,
        tmdb_service_1.TmdbService,
        websocket_gateway_1.WebsocketGateway])
], MoviesService);
//# sourceMappingURL=movies.service.js.map