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
var __param = (this && this.__param) || function (paramIndex, decorator) {
    return function (target, key) { decorator(target, key, paramIndex); }
};
var MoviesService_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.MoviesService = void 0;
const common_1 = require("@nestjs/common");
const typeorm_1 = require("@nestjs/typeorm");
const typeorm_2 = require("typeorm");
const movie_entity_1 = require("../../database/entities/movie.entity");
const user_movie_entity_1 = require("../../database/entities/user-movie.entity");
const tmdb_service_1 = require("../tmdb/tmdb.service");
const database_service_1 = require("../../database/database.service");
const websocket_gateway_1 = require("../websocket/websocket.gateway");
const uuid_1 = require("uuid");
const user_movies_service_1 = require("./user-movies.service");
let MoviesService = MoviesService_1 = class MoviesService {
    constructor(movieRepository, userMovieRepository, tmdbService, databaseService, websocketGateway, userMoviesService) {
        this.movieRepository = movieRepository;
        this.userMovieRepository = userMovieRepository;
        this.tmdbService = tmdbService;
        this.databaseService = databaseService;
        this.websocketGateway = websocketGateway;
        this.userMoviesService = userMoviesService;
        this.logger = new common_1.Logger(MoviesService_1.name);
        this.isInitializing = false;
    }
    async initializeApp() {
        try {
            this.logger.log('controllo inizializzazione database...');
            const moviesCount = await this.movieRepository.count();
            this.logger.log(`film presenti nel database: ${moviesCount}`);
            if (moviesCount >= 1000) {
                this.logger.log('database gia inizializzato');
                return {
                    needsSync: false,
                    moviesInDb: moviesCount,
                    message: 'database gia inizializzato',
                };
            }
            if (this.isInitializing) {
                this.logger.log('inizializzazione gia in corso');
                return {
                    needsSync: true,
                    moviesInDb: moviesCount,
                    message: 'inizializzazione in corso',
                };
            }
            this.isInitializing = true;
            this.logger.log('avvio sync iniziale film popolari...');
            this.syncInitialMovies()
                .then(() => {
                this.logger.log('sync iniziale completato');
                this.isInitializing = false;
            })
                .catch((error) => {
                this.logger.error(`errore sync iniziale: ${error.message}`);
                this.isInitializing = false;
            });
            return {
                needsSync: true,
                moviesInDb: moviesCount,
                message: 'sync avviato in background',
            };
        }
        catch (error) {
            this.logger.error(`errore initializeapp: ${error.message}`);
            this.isInitializing = false;
            throw error;
        }
    }
    async syncInitialMovies() {
        try {
            this.logger.log('inizio sync film popolari...');
            const result = await this.tmdbService.syncPopularMovies(10000);
            this.logger.log(`sync completato: ${result.synced} film sincronizzati, ${result.errors} errori`);
        }
        catch (error) {
            this.logger.error(`errore sync: ${error.message}`);
            throw error;
        }
    }
    async healthCheck() {
        try {
            const movieCount = await this.movieRepository.count();
            const userMovieCount = await this.userMovieRepository.count();
            return {
                status: 'ok',
                database: 'connected',
                movies: movieCount,
                userMovies: userMovieCount,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`health check failed: ${error.message}`);
            return {
                status: 'error',
                database: 'disconnected',
                error: error.message,
                timestamp: new Date().toISOString(),
            };
        }
    }
    async getStats() {
        try {
            const totalMovies = await this.movieRepository.count();
            const enrichedMovies = await this.movieRepository.count({
                where: { tmdb_id: (0, typeorm_2.Not)((0, typeorm_2.IsNull)()) },
            });
            const totalUserMovies = await this.userMovieRepository.count();
            return {
                database: {
                    totalMovies,
                    enrichedMovies,
                    totalUserMovies,
                    enrichmentRate: totalMovies > 0
                        ? ((enrichedMovies / totalMovies) * 100).toFixed(2) + '%'
                        : '0%',
                },
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            this.logger.error(`errore stats: ${error.message}`);
            throw error;
        }
    }
    async enrichMovies(movies) {
        const sessionId = (0, uuid_1.v4)();
        const successfulMovies = [];
        const failedMovies = [];
        let cacheHits = 0;
        const totalMovies = movies.length;
        this.logger.log(`enrichment batch: ${totalMovies} film`);
        await this.websocketGateway.notifyEnrichmentStarted(sessionId, totalMovies);
        for (let i = 0; i < movies.length; i++) {
            const movie = movies[i];
            const currentProgress = i + 1;
            try {
                const existingMovie = await this.databaseService.findMovieByTitleYear(movie.title, movie.year);
                let enrichedMovie;
                if (existingMovie && existingMovie.is_enriched && existingMovie.tmdb_id) {
                    this.logger.debug(`cache hit: ${movie.title}`);
                    enrichedMovie = {
                        ...existingMovie,
                        id: movie.id,
                        source: movie.source
                    };
                    cacheHits++;
                }
                else {
                    this.logger.debug(`enrichment tmdb: ${movie.title}`);
                    enrichedMovie = await this.tmdbService.enrichMovie(movie);
                    if (enrichedMovie.tmdb_id) {
                        await this.databaseService.saveMovie(enrichedMovie);
                    }
                }
                if (enrichedMovie && enrichedMovie.tmdb_id) {
                    successfulMovies.push(enrichedMovie);
                }
                else {
                    failedMovies.push({
                        movie,
                        error: 'no tmdb data found',
                    });
                }
                await this.websocketGateway.notifyEnrichmentProgress(sessionId, currentProgress, totalMovies, movie.title);
                await this.sleep(200);
            }
            catch (error) {
                this.logger.warn(`errore enrichment "${movie.title}": ${error.message}`);
                failedMovies.push({
                    movie,
                    error: error.message,
                });
            }
        }
        await this.websocketGateway.notifyEnrichmentCompleted(sessionId, totalMovies);
        const successRate = totalMovies > 0
            ? (successfulMovies.length / totalMovies) * 100
            : 0;
        this.logger.log(`enrichment completato:`);
        this.logger.log(`   successo: ${successfulMovies.length}/${totalMovies}`);
        this.logger.log(`   cache hits: ${cacheHits}`);
        this.logger.log(`   falliti: ${failedMovies.length}`);
        return {
            sessionId,
            successfulMovies,
            failedMovies,
            totalProcessed: totalMovies,
            successRate,
            cacheHits,
        };
    }
    async batchUpload(watchlist, watched, userId) {
        const sessionId = (0, uuid_1.v4)();
        this.logger.log(`batch upload per user ${userId}:`);
        this.logger.log(`  watchlist: ${watchlist.length} film`);
        this.logger.log(`  watched: ${watched.length} film`);
        const watchlistResult = await this.enrichMovies(watchlist);
        const watchedResult = await this.enrichMovies(watched);
        const fromFile = {
            watched: watched.length,
            watchlist: watchlist.length,
            total: watched.length + watchlist.length,
        };
        await this.userMoviesService.associateMoviesToUser(userId, watchlistResult.successfulMovies, 'watchlist');
        await this.userMoviesService.associateMoviesToUser(userId, watchedResult.successfulMovies, 'watched');
        const stats = await this.userMoviesService.getUserMovieStats(userId);
        const afterRefresh = {
            totalWatched: stats.watched,
            totalWatchlist: stats.watchlist,
            total: stats.total,
        };
        const summary = {
            totalMovies: watchlist.length + watched.length,
            watchlistCount: watchlist.length,
            watchedCount: watched.length,
            totalEnriched: watchlistResult.successfulMovies.length +
                watchedResult.successfulMovies.length,
            overallSuccessRate: ((watchlistResult.successfulMovies.length +
                watchedResult.successfulMovies.length) /
                (watchlist.length + watched.length)) *
                100,
            cacheHitsTotal: watchlistResult.cacheHits + watchedResult.cacheHits,
        };
        return {
            sessionId,
            watchlistResult,
            watchedResult,
            summary,
            counters: {
                fromFile,
                afterRefresh,
            },
        };
    }
    async batchUploadWithUserAssociation(userId, watchlist, watched) {
        this.logger.log(`batch upload per user ${userId}: ${watchlist.length} watchlist, ${watched.length} watched`);
        const watchedFromFile = watched.length;
        const watchlistFromFile = watchlist.length;
        this.logger.log(`enrichment batch: ${watchlist.length + watched.length} film`);
        const watchlistResult = await this.enrichMovies(watchlist);
        const watchedResult = await this.enrichMovies(watched);
        this.logger.log(`associazione film a user ${userId}...`);
        for (const movie of watchlistResult.successfulMovies) {
            try {
                const dbMovie = await this.databaseService.findMovieByTmdbId(movie.tmdb_id);
                if (dbMovie) {
                    await this.userMoviesService.associateMoviesToUser(userId, [{ ...movie, id: dbMovie.id }], 'watchlist');
                }
            }
            catch (error) {
                this.logger.warn(`errore associazione watchlist ${movie.title}: ${error.message}`);
            }
        }
        for (const movie of watchedResult.successfulMovies) {
            try {
                const dbMovie = await this.databaseService.findMovieByTmdbId(movie.tmdb_id);
                if (dbMovie) {
                    await this.userMoviesService.associateMoviesToUser(userId, [{ ...movie, id: dbMovie.id }], 'watched');
                }
            }
            catch (error) {
                this.logger.warn(`errore associazione watched ${movie.title}: ${error.message}`);
            }
        }
        const stats = await this.userMoviesService.getUserMovieStats(userId);
        const summary = {
            totalMovies: watchlist.length + watched.length,
            watchlistCount: watchlist.length,
            watchedCount: watched.length,
            totalEnriched: watchlistResult.successfulMovies.length +
                watchedResult.successfulMovies.length,
            overallSuccessRate: ((watchlistResult.successfulMovies.length +
                watchedResult.successfulMovies.length) /
                (watchlist.length + watched.length)) *
                100,
            cacheHitsTotal: watchlistResult.cacheHits + watchedResult.cacheHits,
        };
        return {
            sessionId: (0, uuid_1.v4)(),
            watchlistResult,
            watchedResult,
            summary,
            importCounters: {
                watchedFromFile,
                watchlistFromFile,
                totalWatched: stats.watched,
                totalWatchlist: stats.watchlist,
            },
        };
    }
    async getAllMovies(userId, status) {
        try {
            this.logger.log(`recupero film per user ${userId} (status: ${status || 'all'})`);
            const queryBuilder = this.userMovieRepository
                .createQueryBuilder('um')
                .leftJoinAndSelect('um.movie', 'movie')
                .where('um.userId = :userId', { userId });
            if (status && ['watched', 'watchlist'].includes(status)) {
                queryBuilder.andWhere('um.status = :status', { status });
            }
            const userMovies = await queryBuilder.getMany();
            const movies = userMovies
                .filter(um => um.movie)
                .map(um => ({
                ...um.movie,
                user_rating: um.userRating,
                watched_date: um.watchedDate,
                user_review: um.userReview,
                is_favorite: um.isFavorite,
            }));
            this.logger.log(`trovati ${movies.length} film`);
            return movies;
        }
        catch (error) {
            this.logger.error(`errore getallmovies: ${error.message}`);
            throw error;
        }
    }
    async getUserStats(userId) {
        try {
            const stats = await this.userMoviesService.getUserMovieStats(userId);
            return stats;
        }
        catch (error) {
            this.logger.error(`errore getstats: ${error.message}`);
            throw error;
        }
    }
    async deleteAllMovies(userId) {
        try {
            this.logger.log(`eliminazione associazioni per user ${userId}`);
            const result = await this.userMovieRepository.delete({ userId });
            const deletedCount = result.affected || 0;
            this.logger.log(`eliminate ${deletedCount} associazioni`);
            return { deleted: deletedCount };
        }
        catch (error) {
            this.logger.error(`errore delete: ${error.message}`);
            throw error;
        }
    }
    sleep(ms) {
        return new Promise(resolve => setTimeout(resolve, ms));
    }
};
exports.MoviesService = MoviesService;
exports.MoviesService = MoviesService = MoviesService_1 = __decorate([
    (0, common_1.Injectable)(),
    __param(0, (0, typeorm_1.InjectRepository)(movie_entity_1.MovieEntity)),
    __param(1, (0, typeorm_1.InjectRepository)(user_movie_entity_1.UserMovieEntity)),
    __metadata("design:paramtypes", [typeorm_2.Repository,
        typeorm_2.Repository,
        tmdb_service_1.TmdbService,
        database_service_1.DatabaseService,
        websocket_gateway_1.WebsocketGateway,
        user_movies_service_1.UserMoviesService])
], MoviesService);
//# sourceMappingURL=movies.service.js.map