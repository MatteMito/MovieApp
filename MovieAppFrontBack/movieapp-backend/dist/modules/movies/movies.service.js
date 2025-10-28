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
const user_entity_1 = require("../../database/entities/user.entity");
const user_movie_entity_1 = require("../../database/entities/user-movie.entity");
const database_service_1 = require("../../database/database.service");
const tmdb_service_1 = require("../tmdb/tmdb.service");
const websocket_gateway_1 = require("../websocket/websocket.gateway");
const uuid_1 = require("uuid");
let MoviesService = MoviesService_1 = class MoviesService {
    constructor(movieRepository, userRepository, userMovieRepository, databaseService, tmdbService, websocketGateway) {
        this.movieRepository = movieRepository;
        this.userRepository = userRepository;
        this.userMovieRepository = userMovieRepository;
        this.databaseService = databaseService;
        this.tmdbService = tmdbService;
        this.websocketGateway = websocketGateway;
        this.logger = new common_1.Logger(MoviesService_1.name);
    }
    async healthCheck() {
        try {
            const movieCount = await this.movieRepository.count();
            return {
                status: 'healthy',
                database: 'connected',
                movieCount,
                timestamp: new Date().toISOString(),
            };
        }
        catch (error) {
            return {
                status: 'unhealthy',
                error: error.message,
                timestamp: new Date().toISOString(),
            };
        }
    }
    async getStats() {
        const totalMovies = await this.movieRepository.count();
        const enrichedMovies = await this.movieRepository.count({
            where: { tmdb_id: (0, typeorm_2.Not)((0, typeorm_2.IsNull)()) },
        });
        return {
            database: {
                totalMovies,
                enrichedMovies,
                enrichmentRate: totalMovies > 0 ? (enrichedMovies / totalMovies) * 100 : 0,
            },
            timestamp: new Date().toISOString(),
        };
    }
    async getUserStats(userId) {
        const totalMovies = await this.userMovieRepository.count({ where: { userId } });
        const watchedCount = await this.userMovieRepository.count({
            where: { userId, status: user_movie_entity_1.MovieStatus.WATCHED },
        });
        const watchlistCount = await this.userMovieRepository.count({
            where: { userId, status: user_movie_entity_1.MovieStatus.WATCHLIST },
        });
        return {
            totalMovies,
            watchedCount,
            watchlistCount,
        };
    }
    async getMovieById(id) {
        return this.movieRepository.findOne({ where: { id } });
    }
    async getAllMovies(userId) {
        const userMovies = await this.userMovieRepository.find({
            where: { userId },
            relations: ['movie'],
            order: { createdAt: 'DESC' },
        });
        return userMovies.map(um => um.movie);
    }
    async deleteAllMovies(userId) {
        const userMovies = await this.userMovieRepository.find({
            where: { userId },
        });
        const deletedCount = userMovies.length;
        await this.userMovieRepository.remove(userMovies);
        this.logger.log(`🗑️ Eliminati ${deletedCount} film per user ${userId}`);
        return {
            message: 'All movies deleted successfully',
            deletedCount,
        };
    }
    async getUserMovies(userId, filters) {
        const queryBuilder = this.userMovieRepository
            .createQueryBuilder('um')
            .leftJoinAndSelect('um.movie', 'movie')
            .where('um.userId = :userId', { userId });
        if (filters.status) {
            queryBuilder.andWhere('um.status = :status', { status: filters.status });
        }
        if (filters.query) {
            queryBuilder.andWhere('movie.title ILIKE :query', { query: `%${filters.query}%` });
        }
        if (filters.genre) {
            queryBuilder.andWhere(':genre = ANY(movie.genres)', { genre: filters.genre });
        }
        if (filters.year) {
            queryBuilder.andWhere('movie.year = :year', { year: filters.year });
        }
        if (filters.director) {
            queryBuilder.andWhere('movie.director ILIKE :director', { director: `%${filters.director}%` });
        }
        if (filters.minRating !== undefined) {
            queryBuilder.andWhere('um.userRating >= :minRating', { minRating: filters.minRating });
        }
        if (filters.maxRating !== undefined) {
            queryBuilder.andWhere('um.userRating <= :maxRating', { maxRating: filters.maxRating });
        }
        const sortBy = filters.sortBy || 'createdAt';
        const sortOrder = filters.sortOrder || 'DESC';
        if (sortBy === 'title') {
            queryBuilder.orderBy('movie.title', sortOrder);
        }
        else if (sortBy === 'year') {
            queryBuilder.orderBy('movie.year', sortOrder);
        }
        else if (sortBy === 'userRating') {
            queryBuilder.orderBy('um.userRating', sortOrder);
        }
        else {
            queryBuilder.orderBy('um.createdAt', sortOrder);
        }
        if (filters.limit) {
            queryBuilder.limit(filters.limit);
        }
        if (filters.offset) {
            queryBuilder.offset(filters.offset);
        }
        const [userMovies, total] = await queryBuilder.getManyAndCount();
        const movies = userMovies.map(um => um.movie);
        return {
            movies,
            total,
        };
    }
    async searchMovies(filters) {
        const queryBuilder = this.movieRepository.createQueryBuilder('movie');
        if (filters.query) {
            queryBuilder.where('movie.title ILIKE :query', { query: `%${filters.query}%` });
        }
        if (filters.genre) {
            queryBuilder.andWhere(':genre = ANY(movie.genres)', { genre: filters.genre });
        }
        if (filters.year) {
            queryBuilder.andWhere('movie.year = :year', { year: filters.year });
        }
        if (filters.director) {
            queryBuilder.andWhere('movie.director ILIKE :director', { director: `%${filters.director}%` });
        }
        if (filters.minRating !== undefined) {
            queryBuilder.andWhere('movie.tmdb_rating >= :minRating', { minRating: filters.minRating });
        }
        if (filters.maxRating !== undefined) {
            queryBuilder.andWhere('movie.tmdb_rating <= :maxRating', { maxRating: filters.maxRating });
        }
        const sortBy = filters.sortBy || 'title';
        const sortOrder = filters.sortOrder || 'ASC';
        queryBuilder.orderBy(`movie.${sortBy}`, sortOrder);
        if (filters.limit) {
            queryBuilder.limit(filters.limit);
        }
        if (filters.offset) {
            queryBuilder.offset(filters.offset);
        }
        const [movies, total] = await queryBuilder.getManyAndCount();
        return {
            movies,
            total,
        };
    }
    async enrichMovies(movies) {
        const sessionId = (0, uuid_1.v4)();
        const successfulMovies = [];
        const failedMovies = [];
        const totalMovies = movies.length;
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
                        error: 'No TMDB data found',
                    });
                }
                await this.websocketGateway.notifyEnrichmentProgress(sessionId, currentProgress, totalMovies, movie.title);
                await this.sleep(300);
            }
            catch (error) {
                this.logger.warn(`⚠️ Errore enrichment "${movie.title}": ${error.message}`);
                failedMovies.push({
                    movie,
                    error: error.message,
                });
            }
        }
        const successRate = (successfulMovies.length / movies.length) * 100;
        return {
            sessionId: (0, uuid_1.v4)(),
            successfulMovies,
            failedMovies,
            totalProcessed: movies.length,
            successRate,
            cacheHits: 0,
        };
    }
    async batchUpload(watchlist, watched, userId) {
        this.logger.log(`📦 Batch upload: ${watchlist.length} watchlist, ${watched.length} watched`);
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
        this.logger.log(`📦 Batch upload per user ${userId}: ${watchlist.length} watchlist, ${watched.length} watched`);
        const result = await this.batchUpload(watchlist, watched, userId);
        const totalWatched = await this.userMovieRepository.count({
            where: { userId, status: user_movie_entity_1.MovieStatus.WATCHED },
        });
        const totalWatchlist = await this.userMovieRepository.count({
            where: { userId, status: user_movie_entity_1.MovieStatus.WATCHLIST },
        });
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
    __param(1, (0, typeorm_1.InjectRepository)(user_entity_1.UserEntity)),
    __param(2, (0, typeorm_1.InjectRepository)(user_movie_entity_1.UserMovieEntity)),
    __metadata("design:paramtypes", [typeorm_2.Repository,
        typeorm_2.Repository,
        typeorm_2.Repository,
        database_service_1.DatabaseService,
        tmdb_service_1.TmdbService,
        websocket_gateway_1.WebsocketGateway])
], MoviesService);
//# sourceMappingURL=movies.service.js.map