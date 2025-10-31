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
var UserMoviesService_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.UserMoviesService = void 0;
const common_1 = require("@nestjs/common");
const typeorm_1 = require("@nestjs/typeorm");
const typeorm_2 = require("typeorm");
const user_movie_entity_1 = require("../../database/entities/user-movie.entity");
let UserMoviesService = UserMoviesService_1 = class UserMoviesService {
    constructor(userMovieRepository) {
        this.userMovieRepository = userMovieRepository;
        this.logger = new common_1.Logger(UserMoviesService_1.name);
    }
    async associateMoviesToUser(userId, movies, status) {
        try {
            this.logger.log(`associazione ${movies.length} film (${status}) a user ${userId}`);
            const movieStatus = status === 'watched' ? user_movie_entity_1.MovieStatus.WATCHED : user_movie_entity_1.MovieStatus.WATCHLIST;
            for (const movie of movies) {
                const existing = await this.userMovieRepository.findOne({
                    where: { userId, movieId: movie.id },
                });
                if (!existing) {
                    const userMovie = this.userMovieRepository.create({
                        userId,
                        movieId: movie.id,
                        status: movieStatus,
                        userRating: movie.user_rating,
                        watchedDate: movie.watched_date ? new Date(movie.watched_date) : null,
                    });
                    await this.userMovieRepository.save(userMovie);
                }
            }
            this.logger.log(`associati ${movies.length} film come ${status}`);
        }
        catch (error) {
            this.logger.error(`errore associateMoviesToUser: ${error.message}`);
            throw error;
        }
    }
    async batchAssociateMovies(userId, movies) {
        try {
            this.logger.log(`batch: ${movies.length} film per utente ${userId}`);
            let created = 0;
            let updated = 0;
            let watchedInFile = 0;
            let watchlistInFile = 0;
            watchedInFile = movies.filter(m => m.status === user_movie_entity_1.MovieStatus.WATCHED).length;
            watchlistInFile = movies.filter(m => m.status === user_movie_entity_1.MovieStatus.WATCHLIST).length;
            const CHUNK_SIZE = 100;
            for (let i = 0; i < movies.length; i += CHUNK_SIZE) {
                const chunk = movies.slice(i, i + CHUNK_SIZE);
                for (const movie of chunk) {
                    const existing = await this.userMovieRepository.findOne({
                        where: { userId, movieId: movie.movieId },
                    });
                    if (existing) {
                        existing.status = movie.status;
                        existing.userRating = movie.userRating;
                        existing.watchedDate = movie.watchedDate;
                        existing.userReview = movie.userReview;
                        await this.userMovieRepository.save(existing);
                        updated++;
                    }
                    else {
                        const userMovie = this.userMovieRepository.create({
                            userId,
                            movieId: movie.movieId,
                            status: movie.status,
                            userRating: movie.userRating,
                            watchedDate: movie.watchedDate,
                            userReview: movie.userReview,
                        });
                        await this.userMovieRepository.save(userMovie);
                        created++;
                    }
                }
                this.logger.log(`chunk ${Math.floor(i / CHUNK_SIZE) + 1}: ${chunk.length} film`);
            }
            this.logger.log(`=== batch completato ===`);
            this.logger.log(`creati: ${created}, aggiornati: ${updated}`);
            this.logger.log(`watched: ${watchedInFile}, watchlist: ${watchlistInFile}`);
            return { created, updated, watchedInFile, watchlistInFile };
        }
        catch (error) {
            this.logger.error(`errore batch: ${error.message}`);
            throw error;
        }
    }
    async getUserMovies(userId, status) {
        try {
            const query = this.userMovieRepository
                .createQueryBuilder('um')
                .leftJoinAndSelect('um.movie', 'movie')
                .where('um.userId = :userId', { userId });
            if (status) {
                query.andWhere('um.status = :status', { status });
            }
            const userMovies = await query.getMany();
            const movies = userMovies.map(um => ({
                id: um.movie.id,
                title: um.movie.title,
                year: um.movie.year,
                source: um.movie.source,
                tmdb_id: um.movie.tmdb_id,
                director: um.movie.director,
                genres: um.movie.genres,
                actors: um.movie.actors,
                overview: um.movie.overview,
                tagline: um.movie.tagline,
                runtime: um.movie.runtime,
                poster_url: um.movie.poster_url,
                backdrop_url: um.movie.backdrop_url,
                tmdb_rating: um.movie.tmdb_rating,
                vote_count: um.movie.vote_count,
                budget: um.movie.budget,
                revenue: um.movie.revenue,
                status: um.movie.status,
                original_language: um.movie.original_language,
                original_title: um.movie.original_title,
                popularity: um.movie.popularity,
                adult: um.movie.adult,
                homepage: um.movie.homepage,
                imdb_id: um.movie.imdb_id,
                production_companies: um.movie.production_companies,
                production_countries: um.movie.production_countries,
                spoken_languages: um.movie.spoken_languages,
                keywords: um.movie.keywords,
                certification: um.movie.certification,
                trailer_url: um.movie.trailer_url,
                user_rating: um.userRating,
                watched_date: um.watchedDate?.toISOString(),
                is_watched: um.status === user_movie_entity_1.MovieStatus.WATCHED,
            }));
            this.logger.debug(`recuperati ${movies.length} film per utente ${userId}`);
            return movies;
        }
        catch (error) {
            this.logger.error(`errore getUserMovies: ${error.message}`);
            return [];
        }
    }
    async getUserMovieStats(userId) {
        try {
            const [watched, watchlist, allUserMovies] = await Promise.all([
                this.userMovieRepository.count({
                    where: { userId, status: user_movie_entity_1.MovieStatus.WATCHED },
                }),
                this.userMovieRepository.count({
                    where: { userId, status: user_movie_entity_1.MovieStatus.WATCHLIST },
                }),
                this.userMovieRepository.find({
                    where: { userId },
                }),
            ]);
            const ratingsWithValues = allUserMovies
                .map(um => um.userRating)
                .filter((r) => r !== null && r !== undefined);
            const averageRating = ratingsWithValues.length > 0
                ? ratingsWithValues.reduce((sum, r) => sum + r, 0) / ratingsWithValues.length
                : 0;
            const totalMovies = watched + watchlist;
            this.logger.debug(`stats utente ${userId}: ${totalMovies} film (${watched} visti, ${watchlist} da vedere)`);
            return {
                userId,
                totalMovies,
                watchedCount: watched,
                watchlistCount: watchlist,
                averageRating: parseFloat(averageRating.toFixed(2)),
                watched,
                watchlist,
                total: totalMovies,
            };
        }
        catch (error) {
            this.logger.error(`errore getUserMovieStats: ${error.message}`);
            return {
                userId,
                totalMovies: 0,
                watchedCount: 0,
                watchlistCount: 0,
                averageRating: 0,
                watched: 0,
                watchlist: 0,
                total: 0,
            };
        }
    }
    async getImportCounters(userId) {
        try {
            const stats = await this.getUserMovieStats(userId);
            return {
                watchedFromFile: 0,
                watchlistFromFile: 0,
                totalWatched: stats.watchedCount,
                totalWatchlist: stats.watchlistCount,
            };
        }
        catch (error) {
            this.logger.error(`errore getImportCounters: ${error.message}`);
            return {
                watchedFromFile: 0,
                watchlistFromFile: 0,
                totalWatched: 0,
                totalWatchlist: 0,
            };
        }
    }
};
exports.UserMoviesService = UserMoviesService;
exports.UserMoviesService = UserMoviesService = UserMoviesService_1 = __decorate([
    (0, common_1.Injectable)(),
    __param(0, (0, typeorm_1.InjectRepository)(user_movie_entity_1.UserMovieEntity)),
    __metadata("design:paramtypes", [typeorm_2.Repository])
], UserMoviesService);
//# sourceMappingURL=user-movies.service.js.map