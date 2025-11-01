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
const user_movie_entity_2 = require("../../database/entities/user-movie.entity");
let MoviesService = MoviesService_1 = class MoviesService {
    constructor(movieRepository, userMovieRepository, tmdbService, databaseService, websocketGateway, userMoviesService) {
        this.movieRepository = movieRepository;
        this.userMovieRepository = userMovieRepository;
        this.tmdbService = tmdbService;
        this.databaseService = databaseService;
        this.websocketGateway = websocketGateway;
        this.userMoviesService = userMoviesService;
        this.logger = new common_1.Logger(MoviesService_1.name);
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
                    enrichmentRate: totalMovies > 0 ? (enrichedMovies / totalMovies) * 100 : 0,
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
        this.logger.log(`🎬 enrichment batch: ${totalMovies} film`);
        await this.websocketGateway.notifyEnrichmentStarted(sessionId, totalMovies);
        for (let i = 0; i < movies.length; i++) {
            const movie = movies[i];
            const currentProgress = i + 1;
            try {
                const existingMovie = await this.databaseService.findMovieByTitleYear(movie.title, movie.year);
                let enrichedMovie;
                if (existingMovie && existingMovie.is_enriched && existingMovie.tmdb_id) {
                    this.logger.debug(`💰 cache hit: ${movie.title}`);
                    enrichedMovie = {
                        ...existingMovie,
                        id: movie.id,
                        source: movie.source
                    };
                    cacheHits++;
                }
                else {
                    this.logger.debug(`🔍 enrichment tmdb: ${movie.title}`);
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
        this.logger.log(`✅ enrichment completato:`);
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
        const watchlistResult = await this.enrichMovies(watchlist);
        const watchedResult = await this.enrichMovies(watched);
        this.logger.log(`creazione associazioni user_movies...`);
        const watchlistMovies = watchlistResult.successfulMovies;
        const watchedMovies = watchedResult.successfulMovies;
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
                        status: user_movie_entity_2.MovieStatus.WATCHLIST,
                        userRating: movie.user_rating || null,
                        watchedDate: null,
                    });
                    await this.userMovieRepository.save(userMovie);
                    watchlistCreated++;
                }
            }
            catch (error) {
                this.logger.warn(`errore associazione watchlist ${movie.title}: ${error.message}`);
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
                        status: user_movie_entity_2.MovieStatus.WATCHED,
                        userRating: movie.user_rating || null,
                        watchedDate: movie.watched_date ? new Date(movie.watched_date) : null,
                    });
                    await this.userMovieRepository.save(userMovie);
                    watchedCreated++;
                }
            }
            catch (error) {
                this.logger.warn(`errore associazione watched ${movie.title}: ${error.message}`);
            }
        }
        this.logger.log(`associazioni create: ${watchlistCreated} watchlist, ${watchedCreated} watched`);
        const stats = await this.userMoviesService.getUserMovieStats(userId);
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
                cacheHitsTotal: watchlistResult.cacheHits + watchedResult.cacheHits,
            },
            importCounters: {
                watchedFromFile,
                watchlistFromFile,
                totalWatched: stats.watched,
                totalWatchlist: stats.watchlist,
            },
        };
    }
    async getUserMovies(userId, filters) {
        try {
            this.logger.log(`recupero film per user ${userId} con filtri:`, filters);
            const movies = await this.userMoviesService.getUserMovies(userId, filters.status);
            let filteredMovies = movies;
            if (filters.query) {
                const query = filters.query.toLowerCase();
                filteredMovies = filteredMovies.filter((m) => m.title.toLowerCase().includes(query) ||
                    m.director?.toLowerCase().includes(query));
            }
            if (filters.genre) {
                filteredMovies = filteredMovies.filter((m) => m.genres?.some((g) => g.toLowerCase().includes(filters.genre.toLowerCase())));
            }
            if (filters.year) {
                filteredMovies = filteredMovies.filter((m) => m.year === filters.year);
            }
            if (filters.director) {
                filteredMovies = filteredMovies.filter((m) => m.director?.toLowerCase().includes(filters.director.toLowerCase()));
            }
            if (filters.minRating) {
                filteredMovies = filteredMovies.filter((m) => (m.tmdb_rating || 0) >= filters.minRating);
            }
            if (filters.maxRating) {
                filteredMovies = filteredMovies.filter((m) => (m.tmdb_rating || 0) <= filters.maxRating);
            }
            if (filters.sortBy) {
                filteredMovies.sort((a, b) => {
                    const aVal = a[filters.sortBy] || '';
                    const bVal = b[filters.sortBy] || '';
                    if (filters.sortOrder === 'DESC') {
                        return bVal > aVal ? 1 : -1;
                    }
                    return aVal > bVal ? 1 : -1;
                });
            }
            const total = filteredMovies.length;
            if (filters.limit) {
                const offset = filters.offset || 0;
                filteredMovies = filteredMovies.slice(offset, offset + filters.limit);
            }
            return { movies: filteredMovies, total };
        }
        catch (error) {
            this.logger.error(`errore getUserMovies: ${error.message}`);
            throw error;
        }
    }
    async getUserStats(userId) {
        try {
            return await this.userMoviesService.getUserMovieStats(userId);
        }
        catch (error) {
            this.logger.error(`errore getUserStats: ${error.message}`);
            throw error;
        }
    }
    async getMovieById(id) {
        try {
            const movie = await this.movieRepository.findOne({ where: { id } });
            return movie;
        }
        catch (error) {
            this.logger.error(`errore getMovieById: ${error.message}`);
            return null;
        }
    }
    async searchMovies(filters) {
        try {
            const queryBuilder = this.movieRepository.createQueryBuilder('movie');
            if (filters.query) {
                queryBuilder.andWhere('(LOWER(movie.title) LIKE :query OR LOWER(movie.director) LIKE :query)', { query: `%${filters.query.toLowerCase()}%` });
            }
            if (filters.genre) {
                queryBuilder.andWhere(':genre = ANY(movie.genres)', {
                    genre: filters.genre,
                });
            }
            if (filters.year) {
                queryBuilder.andWhere('movie.year = :year', { year: filters.year });
            }
            if (filters.director) {
                queryBuilder.andWhere('LOWER(movie.director) LIKE :director', {
                    director: `%${filters.director.toLowerCase()}%`,
                });
            }
            if (filters.minRating !== undefined) {
                queryBuilder.andWhere('movie.tmdb_rating >= :minRating', {
                    minRating: filters.minRating,
                });
            }
            if (filters.maxRating !== undefined) {
                queryBuilder.andWhere('movie.tmdb_rating <= :maxRating', {
                    maxRating: filters.maxRating,
                });
            }
            const total = await queryBuilder.getCount();
            const sortBy = filters.sortBy || 'title';
            const sortOrder = filters.sortOrder || 'ASC';
            queryBuilder.orderBy(`movie.${sortBy}`, sortOrder);
            if (filters.limit) {
                queryBuilder.limit(filters.limit);
            }
            if (filters.offset) {
                queryBuilder.offset(filters.offset);
            }
            const movies = await queryBuilder.getMany();
            return { movies: movies, total };
        }
        catch (error) {
            this.logger.error(`errore searchMovies: ${error.message}`);
            throw error;
        }
    }
    async getAllMovies(userId) {
        try {
            const result = await this.getUserMovies(userId, {});
            return result.movies;
        }
        catch (error) {
            this.logger.error(`errore getAllMovies: ${error.message}`);
            throw error;
        }
    }
    async deleteAllMovies(userId) {
        try {
            const result = await this.userMovieRepository.delete({ userId });
            this.logger.log(`eliminati ${result.affected} film per user ${userId}`);
            return { deleted: result.affected || 0 };
        }
        catch (error) {
            this.logger.error(`errore deleteAllMovies: ${error.message}`);
            throw error;
        }
    }
    sleep(ms) {
        return new Promise((resolve) => setTimeout(resolve, ms));
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