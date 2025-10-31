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
let MoviesService = MoviesService_1 = class MoviesService {
    constructor(movieRepository, userMovieRepository, tmdbService, databaseService, websocketGateway) {
        this.movieRepository = movieRepository;
        this.userMovieRepository = userMovieRepository;
        this.tmdbService = tmdbService;
        this.databaseService = databaseService;
        this.websocketGateway = websocketGateway;
        this.logger = new common_1.Logger(MoviesService_1.name);
    }
    async enrichMovies(movies) {
        const sessionId = (0, uuid_1.v4)();
        const successfulMovies = [];
        const failedMovies = [];
        const totalMovies = movies.length;
        await this.websocketGateway.notifyEnrichmentStarted(sessionId, totalMovies);
        for (let i = 0; i < movies.length; i++) {
            const movie = movies[i];
            const currentProgress = i + 1;
            try {
                const enrichedMovie = await this.tmdbService.enrichMovie(movie);
                if (enrichedMovie && enrichedMovie.tmdb_id) {
                    successfulMovies.push(enrichedMovie);
                    await this.databaseService.saveMovie(enrichedMovie);
                }
                else {
                    failedMovies.push({
                        movie,
                        error: 'no tmdb data found',
                    });
                }
                await this.websocketGateway.notifyEnrichmentProgress(sessionId, currentProgress, totalMovies, movie.title);
                await this.sleep(300);
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
        const successRate = (successfulMovies.length / movies.length) * 100;
        return {
            sessionId,
            successfulMovies,
            failedMovies,
            totalProcessed: movies.length,
            successRate,
            cacheHits: 0,
        };
    }
    async batchUpload(watchlist, watched, userId) {
        this.logger.log(`batch upload: ${watchlist.length} watchlist, ${watched.length} watched`);
        const [watchlistResult, watchedResult] = await Promise.all([
            this.enrichMovies(watchlist),
            this.enrichMovies(watched),
        ]);
        const totalEnriched = watchlistResult.successfulMovies.length + watchedResult.successfulMovies.length;
        const totalProcessed = watchlistResult.totalProcessed + watchedResult.totalProcessed;
        return {
            sessionId: (0, uuid_1.v4)(),
            watchlistResult,
            watchedResult,
            summary: {
                totalMovies: totalProcessed,
                watchlistCount: watchlist.length,
                watchedCount: watched.length,
                totalEnriched,
                overallSuccessRate: (totalEnriched / totalProcessed) * 100,
                cacheHitsTotal: 0,
            },
        };
    }
    async batchUploadWithUserAssociation(userId, watchlist, watched) {
        this.logger.log(`batch upload per user ${userId}: ${watchlist.length} watchlist, ${watched.length} watched`);
        const result = await this.batchUpload(watchlist, watched, userId);
        this.logger.log(`creazione associazioni user_movies...`);
        const watchlistMovies = result.watchlistResult.successfulMovies;
        const watchedMovies = result.watchedResult.successfulMovies;
        let watchedCreated = 0;
        let watchlistCreated = 0;
        for (const movie of watchlistMovies) {
            try {
                const existing = await this.userMovieRepository.findOne({
                    where: {
                        userId: userId,
                        movieId: movie.id,
                    },
                });
                if (!existing) {
                    const userMovie = this.userMovieRepository.create({
                        userId: userId,
                        movieId: movie.id,
                        status: user_movie_entity_1.MovieStatus.WATCHLIST,
                        userRating: null,
                        watchedDate: null,
                    });
                    await this.userMovieRepository.save(userMovie);
                    watchlistCreated++;
                    this.logger.debug(`associato watchlist: ${movie.title}`);
                }
            }
            catch (error) {
                this.logger.warn(`errore associazione ${movie.title}: ${error.message}`);
            }
        }
        for (const movie of watchedMovies) {
            try {
                const existing = await this.userMovieRepository.findOne({
                    where: {
                        userId: userId,
                        movieId: movie.id,
                    },
                });
                if (!existing) {
                    const userMovie = this.userMovieRepository.create({
                        userId: userId,
                        movieId: movie.id,
                        status: user_movie_entity_1.MovieStatus.WATCHED,
                        userRating: movie.user_rating || null,
                        watchedDate: movie.watched_date ? new Date(movie.watched_date) : null,
                    });
                    await this.userMovieRepository.save(userMovie);
                    watchedCreated++;
                    this.logger.debug(`associato watched: ${movie.title}`);
                }
            }
            catch (error) {
                this.logger.warn(`errore associazione ${movie.title}: ${error.message}`);
            }
        }
        this.logger.log(`associazioni create: ${watchedCreated} watched, ${watchlistCreated} watchlist`);
        const totalWatched = await this.userMovieRepository.count({
            where: { userId, status: user_movie_entity_1.MovieStatus.WATCHED },
        });
        const totalWatchlist = await this.userMovieRepository.count({
            where: { userId, status: user_movie_entity_1.MovieStatus.WATCHLIST },
        });
        this.logger.log(`totali database: ${totalWatched} watched, ${totalWatchlist} watchlist`);
        return {
            ...result,
            importCounters: {
                watchedFromFile: watched.length,
                watchlistFromFile: watchlist.length,
                totalWatched,
                totalWatchlist,
            },
        };
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
        websocket_gateway_1.WebsocketGateway])
], MoviesService);
//# sourceMappingURL=movies.service.js.map