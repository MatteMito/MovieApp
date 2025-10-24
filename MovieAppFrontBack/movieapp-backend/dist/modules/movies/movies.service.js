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
            await this.databaseService.saveMovies(allMovies);
            this.logger.log(`✅ salvati ${allMovies.length} film in tabella movies`);
            await this.websocketGateway.notifyEnrichmentStarted(sessionId, allMovies.length);
            this.logger.log(`🎬 enrichment watchlist...`);
            const watchlistEnrichment = await this.tmdbService.enrichMovies(watchlist, {
                onProgress: async (processed, total, currentMovie) => {
                    await this.websocketGateway.notifyEnrichmentProgress(sessionId, processed, total, currentMovie);
                }
            });
            const watchlistResult = {
                sessionId: (0, uuid_1.v4)(),
                successfulMovies: watchlistEnrichment.successfulMovies,
                failedMovies: watchlistEnrichment.failedMovies,
                totalProcessed: watchlistEnrichment.totalProcessed,
                successRate: watchlistEnrichment.successRate,
                cacheHits: 0,
            };
            this.logger.log(`🎬 enrichment watched...`);
            const watchedEnrichment = await this.tmdbService.enrichMovies(watched, {
                onProgress: async (processed, total, currentMovie) => {
                    await this.websocketGateway.notifyEnrichmentProgress(sessionId, watchlist.length + processed, allMovies.length, currentMovie);
                }
            });
            const watchedResult = {
                sessionId: (0, uuid_1.v4)(),
                successfulMovies: watchedEnrichment.successfulMovies,
                failedMovies: watchedEnrichment.failedMovies,
                totalProcessed: watchedEnrichment.totalProcessed,
                successRate: watchedEnrichment.successRate,
                cacheHits: 0,
            };
            this.logger.log(`🔗 creazione associazioni user_movies...`);
            const watchlistAssociations = watchlistResult.successfulMovies.map(m => ({
                movieId: m.id,
                status: user_movie_entity_1.MovieStatus.WATCHLIST,
            }));
            const watchedAssociations = watchedResult.successfulMovies.map(m => ({
                movieId: m.id,
                status: user_movie_entity_1.MovieStatus.WATCHED,
            }));
            await this.userMoviesService.batchAssociateMovies(userId, [...watchlistAssociations, ...watchedAssociations]);
            this.logger.log(`✅ associazioni create per ${watchlistAssociations.length + watchedAssociations.length} film`);
            const userStats = await this.userMoviesService.getUserMovieStats(userId);
            const importCounters = {
                watchedFromFile: watched.length,
                watchlistFromFile: watchlist.length,
                totalWatched: userStats.watchedCount,
                totalWatchlist: userStats.watchlistCount,
            };
            const totalMovies = watchlist.length + watched.length;
            const totalEnriched = watchlistResult.successfulMovies.filter((m) => m.tmdb_id).length +
                watchedResult.successfulMovies.filter((m) => m.tmdb_id).length;
            const totalCacheHits = watchlistResult.cacheHits + watchedResult.cacheHits;
            await this.websocketGateway.notifyEnrichmentCompleted(sessionId, totalMovies, totalEnriched, totalCacheHits);
            const duration = Date.now() - startTime;
            this.logger.log(`=== BATCH UPLOAD COMPLETATO ===`);
            this.logger.log(`durata: ${(duration / 1000).toFixed(1)}s`);
            this.logger.log(`film arricchiti: ${totalEnriched}/${totalMovies}`);
            return {
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
        }
        catch (error) {
            this.logger.error(`❌ errore batch upload: ${error.message}`);
            await this.websocketGateway.notifyEnrichmentError(sessionId, error.message);
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
            const userMoviesData = await this.userMoviesService.getUserMovies(userId, movieStatus);
            let movies = userMoviesData;
            if (filters?.query) {
                const queryLower = filters.query.toLowerCase();
                movies = movies.filter(m => m.title.toLowerCase().includes(queryLower) ||
                    m.director?.toLowerCase().includes(queryLower));
            }
            if (filters?.genre) {
                movies = movies.filter(m => m.genres?.some(g => g.toLowerCase().includes(filters.genre.toLowerCase())));
            }
            if (filters?.year) {
                movies = movies.filter(m => m.year === filters.year);
            }
            if (filters?.director) {
                movies = movies.filter(m => m.director?.toLowerCase().includes(filters.director.toLowerCase()));
            }
            const total = movies.length;
            if (filters?.sortBy) {
                movies = this.sortMovies(movies, filters.sortBy, filters.sortOrder);
            }
            const offset = filters?.offset || 0;
            const limit = filters?.limit || 50;
            movies = movies.slice(offset, offset + limit);
            this.logger.debug(`✅ trovati ${total} film (mostrati ${movies.length})`);
            return { movies, total };
        }
        catch (error) {
            this.logger.error(`errore getUserMovies: ${error.message}`);
            return { movies: [], total: 0 };
        }
    }
    async getUserStats(userId) {
        try {
            const userStats = await this.userMoviesService.getUserMovieStats(userId);
            return {
                user_id: userId,
                total_movies: userStats.totalMovies,
                watched_count: userStats.watchedCount,
                watchlist_count: userStats.watchlistCount,
                average_rating: userStats.averageRating,
            };
        }
        catch (error) {
            this.logger.error(`errore getUserStats: ${error.message}`);
            return { error: error.message };
        }
    }
    async enrichMovies(movies) {
        try {
            const result = await this.tmdbService.enrichMovies(movies);
            return {
                sessionId: (0, uuid_1.v4)(),
                successfulMovies: result.successfulMovies,
                failedMovies: result.failedMovies,
                totalProcessed: result.totalProcessed,
                successRate: result.successRate,
                cacheHits: 0,
            };
        }
        catch (error) {
            this.logger.error(`errore enrichMovies: ${error.message}`);
            throw error;
        }
    }
    async searchMovies(filters) {
        try {
            let movies = await this.databaseService.getAllMovies();
            if (filters.query) {
                const queryLower = filters.query.toLowerCase();
                movies = movies.filter(m => m.title.toLowerCase().includes(queryLower) ||
                    m.director?.toLowerCase().includes(queryLower));
            }
            if (filters.genre) {
                movies = movies.filter(m => m.genres?.some(g => g.toLowerCase().includes(filters.genre.toLowerCase())));
            }
            if (filters.year) {
                movies = movies.filter(m => m.year === filters.year);
            }
            if (filters.director) {
                movies = movies.filter(m => m.director?.toLowerCase().includes(filters.director.toLowerCase()));
            }
            const total = movies.length;
            if (filters.sortBy) {
                movies = this.sortMovies(movies, filters.sortBy, filters.sortOrder);
            }
            const offset = filters.offset || 0;
            const limit = filters.limit || 50;
            movies = movies.slice(offset, offset + limit);
            return { movies, total };
        }
        catch (error) {
            this.logger.error(`errore searchMovies: ${error.message}`);
            return { movies: [], total: 0 };
        }
    }
    sortMovies(movies, sortBy, sortOrder) {
        const order = sortOrder || 'ASC';
        const sorted = [...movies].sort((a, b) => {
            let comparison = 0;
            switch (sortBy) {
                case 'title':
                    comparison = a.title.localeCompare(b.title);
                    break;
                case 'year':
                    comparison = (a.year || 0) - (b.year || 0);
                    break;
                case 'director':
                    comparison = (a.director || '').localeCompare(b.director || '');
                    break;
                case 'rating':
                    comparison = (a.tmdb_rating || 0) - (b.tmdb_rating || 0);
                    break;
                case 'runtime':
                    comparison = (a.runtime || 0) - (b.runtime || 0);
                    break;
                default:
                    comparison = 0;
            }
            return order === 'DESC' ? -comparison : comparison;
        });
        return sorted;
    }
    async healthCheck() {
        return {
            status: 'ok',
            timestamp: new Date().toISOString(),
        };
    }
    async getStats() {
        try {
            const allMovies = await this.databaseService.getAllMovies();
            const enrichedMovies = allMovies.filter(m => m.tmdb_id);
            return {
                totalMovies: allMovies.length,
                enrichedMovies: enrichedMovies.length,
                enrichmentRate: allMovies.length > 0
                    ? (enrichedMovies.length / allMovies.length) * 100
                    : 0,
            };
        }
        catch (error) {
            this.logger.error(`errore getStats: ${error.message}`);
            return { error: error.message };
        }
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
    async getMovieById(id) {
        try {
            return await this.databaseService.getMovieById(id);
        }
        catch (error) {
            this.logger.error(`errore getMovieById: ${error.message}`);
            return null;
        }
    }
    async deleteAllMovies() {
        try {
            const allMovies = await this.databaseService.getAllMovies();
            for (const movie of allMovies) {
                await this.databaseService.deleteMovie(movie.id);
            }
            this.logger.log('🗑️ tutti i film eliminati');
        }
        catch (error) {
            this.logger.error(`errore deleteAllMovies: ${error.message}`);
            throw error;
        }
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