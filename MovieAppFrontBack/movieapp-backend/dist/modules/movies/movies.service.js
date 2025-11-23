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
            const count = await this.movieRepository.count();
            return {
                status: 'ok',
                timestamp: new Date().toISOString(),
                database: 'connected',
                moviesCount: count,
            };
        }
        catch (error) {
            this.logger.error(`health check fallito: ${error.message}`);
            return {
                status: 'error',
                timestamp: new Date().toISOString(),
                database: 'disconnected',
                moviesCount: 0,
            };
        }
    }
    async initializeApp() {
        try {
            this.logger.log('verifica necessita inizializzazione database...');
            const movieCount = await this.movieRepository.count();
            const enrichedCount = await this.movieRepository.count({
                where: { is_enriched: true },
            });
            this.logger.log(`database: ${movieCount} film, ${enrichedCount} arricchiti`);
            if (movieCount === 0) {
                this.logger.log('🎬 primo avvio! avvio sync 10.000 film popolari in background...');
                this.tmdbService.syncPopularMovies(10000)
                    .then(result => {
                    this.logger.log(`✅ sync iniziale completato: ${result.synced} film`);
                })
                    .catch(error => {
                    this.logger.error(`❌ errore sync iniziale: ${error.message}`);
                });
                return {
                    needsSync: true,
                    message: 'primo avvio: scaricamento 10k film popolari avviato in background',
                    stats: { movieCount: 0, enrichedCount: 0 },
                };
            }
            const unenrichedCount = movieCount - enrichedCount;
            if (unenrichedCount > 0) {
                this.logger.log(`trovati ${unenrichedCount} film non arricchiti`);
                return {
                    needsSync: true,
                    message: `inizializzazione completata: ${enrichedCount}/${movieCount} film arricchiti`,
                    stats: {
                        movieCount,
                        enrichedCount,
                        unenrichedCount,
                    },
                };
            }
            return {
                needsSync: false,
                message: 'database gia inizializzato',
                stats: { movieCount, enrichedCount },
            };
        }
        catch (error) {
            this.logger.error(`errore inizializzazione: ${error.message}`);
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
        this.websocketGateway.notifyEnrichmentStarted(sessionId, totalMovies);
        for (let i = 0; i < movies.length; i++) {
            const movie = movies[i];
            try {
                const existingMovie = await this.databaseService.findMovieById(movie.id);
                if (existingMovie?.is_enriched) {
                    this.logger.log(`cache hit: ${movie.title} (${movie.id})`);
                    successfulMovies.push(existingMovie);
                    cacheHits++;
                }
                else {
                    const enrichedMovie = await this.tmdbService.enrichMovie(movie);
                    if (enrichedMovie) {
                        await this.databaseService.saveMovie(enrichedMovie);
                        successfulMovies.push(enrichedMovie);
                    }
                    else {
                        failedMovies.push({
                            movie,
                            error: 'enrichment fallito',
                        });
                    }
                }
                this.websocketGateway.notifyEnrichmentProgress(sessionId, i + 1, totalMovies, movie.title);
            }
            catch (error) {
                this.logger.error(`errore enrichment ${movie.title}: ${error.message}`);
                failedMovies.push({
                    movie,
                    error: error.message,
                });
            }
        }
        this.websocketGateway.notifyEnrichmentCompleted(sessionId, totalMovies);
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
        this.logger.log(`=== BATCH UPLOAD INIZIO ===`);
        this.logger.log(`user: ${userId}`);
        this.logger.log(`watchlist: ${watchlist.length} film`);
        this.logger.log(`watched: ${watched.length} film`);
        const watchedFromFile = watched.length;
        const watchlistFromFile = watchlist.length;
        this.logger.log(`enrichment batch: ${watchlist.length} film watchlist`);
        const watchlistResult = await this.enrichMovies(watchlist);
        this.logger.log(`enrichment batch: ${watched.length} film watched`);
        const watchedResult = await this.enrichMovies(watched);
        this.logger.log(`=== ASSOCIAZIONE BATCH ===`);
        this.logger.log(`associazione ${watchlistResult.successfulMovies.length} film come WATCHLIST`);
        await this.userMoviesService.associateMoviesToUser(userId, watchlistResult.successfulMovies, 'watchlist');
        this.logger.log(`associazione ${watchedResult.successfulMovies.length} film come WATCHED`);
        await this.userMoviesService.associateMoviesToUser(userId, watchedResult.successfulMovies, 'watched');
        this.logger.log(`recupero statistiche finali...`);
        const stats = await this.userMoviesService.getUserMovieStats(userId);
        this.logger.log(`=== CONTATORI FINALI ===`);
        this.logger.log(`watched totali: ${stats.watched}`);
        this.logger.log(`watchlist totali: ${stats.watchlist}`);
        this.logger.log(`totale: ${stats.total}`);
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
    async getUserMovies(userId, status) {
        try {
            this.logger.log(`recupero film per user ${userId} (status: ${status || 'all'})`);
            let movieStatus;
            if (status === 'watched') {
                movieStatus = user_movie_entity_2.MovieStatus.WATCHED;
            }
            else if (status === 'watchlist') {
                movieStatus = user_movie_entity_2.MovieStatus.WATCHLIST;
            }
            const userMovies = await this.userMoviesService.getUserMovies(userId, movieStatus);
            this.logger.log(`trovati ${userMovies.length} film`);
            return userMovies;
        }
        catch (error) {
            this.logger.error(`errore recupero film user: ${error.message}`);
            throw error;
        }
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
                ...this.entityToMovie(um.movie),
                status: um.status,
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
    async searchMovies(query) {
        try {
            const entities = await this.movieRepository
                .createQueryBuilder('movie')
                .where('LOWER(movie.title) LIKE LOWER(:query)', {
                query: `%${query}%`,
            })
                .orderBy('movie.title', 'ASC')
                .take(50)
                .getMany();
            return entities.map((entity) => this.entityToMovie(entity));
        }
        catch (error) {
            this.logger.error(`errore ricerca film: ${error.message}`);
            throw error;
        }
    }
    async getUserStats(userId) {
        try {
            return await this.userMoviesService.getUserMovieStats(userId);
        }
        catch (error) {
            this.logger.error(`errore recupero user stats: ${error.message}`);
            throw error;
        }
    }
    async getStats() {
        try {
            const totalMovies = await this.movieRepository.count();
            const enrichedMovies = await this.movieRepository.count({
                where: { is_enriched: true },
            });
            const withTmdbId = await this.movieRepository.count({
                where: { tmdb_id: (0, typeorm_2.Not)((0, typeorm_2.IsNull)()) },
            });
            return {
                totalMovies,
                enrichedMovies,
                notEnriched: totalMovies - enrichedMovies,
                withTmdbId,
                enrichmentRate: totalMovies > 0 ? (enrichedMovies / totalMovies) * 100 : 0,
            };
        }
        catch (error) {
            this.logger.error(`errore recupero stats: ${error.message}`);
            throw error;
        }
    }
    entityToMovie(entity) {
        return {
            id: entity.id,
            title: entity.title,
            year: entity.year,
            source: entity.source,
            tmdb_id: entity.tmdb_id,
            is_enriched: entity.is_enriched,
            genres: entity.genres,
            director: entity.director,
            actors: entity.actors,
            overview: entity.overview,
            tagline: entity.tagline,
            runtime: entity.runtime,
            poster_url: entity.poster_url,
            backdrop_url: entity.backdrop_url,
            tmdb_rating: entity.tmdb_rating,
            vote_count: entity.vote_count,
            popularity: entity.popularity,
            budget: entity.budget,
            revenue: entity.revenue,
            status: entity.status,
            production_companies: entity.production_companies,
            production_countries: entity.production_countries,
            original_language: entity.original_language,
            original_title: entity.original_title,
            spoken_languages: entity.spoken_languages,
            adult: entity.adult,
            homepage: entity.homepage,
            imdb_id: entity.imdb_id,
            keywords: entity.keywords,
            certification: entity.certification,
            trailer_url: entity.trailer_url,
            created_at: entity.created_at,
            updated_at: entity.updated_at,
        };
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