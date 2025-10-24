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
const movie_entity_1 = require("../../database/entities/movie.entity");
let UserMoviesService = UserMoviesService_1 = class UserMoviesService {
    constructor(userMovieRepository, movieRepository) {
        this.userMovieRepository = userMovieRepository;
        this.movieRepository = movieRepository;
        this.logger = new common_1.Logger(UserMoviesService_1.name);
    }
    async associateMovieToUser(userId, movieId, status, source, userRating, watchedDate, userReview) {
        try {
            let userMovie = await this.userMovieRepository.findOne({
                where: { userId, movieId },
            });
            if (userMovie) {
                userMovie.status = status;
                userMovie.source = source;
                userMovie.userRating = userRating;
                userMovie.watchedDate = watchedDate;
                userMovie.userReview = userReview;
                this.logger.debug(`🔄 Aggiornato: user ${userId} - movie ${movieId}`);
            }
            else {
                userMovie = this.userMovieRepository.create({
                    userId,
                    movieId,
                    status,
                    source,
                    userRating,
                    watchedDate,
                    userReview,
                });
                this.logger.debug(`➕ Creato: user ${userId} - movie ${movieId} [${status}]`);
            }
            return await this.userMovieRepository.save(userMovie);
        }
        catch (error) {
            this.logger.error(`Errore associazione: ${error.message}`);
            throw error;
        }
    }
    async batchAssociateMoviesToUser(userId, movies) {
        try {
            this.logger.log(`📦 Batch: ${movies.length} film per utente ${userId}`);
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
                        existing.source = movie.source;
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
                            source: movie.source,
                            userRating: movie.userRating,
                            watchedDate: movie.watchedDate,
                            userReview: movie.userReview,
                        });
                        await this.userMovieRepository.save(userMovie);
                        created++;
                    }
                }
                this.logger.log(`✅ Chunk ${Math.floor(i / CHUNK_SIZE) + 1}: ${chunk.length} film`);
            }
            this.logger.log(`=== BATCH COMPLETATO ===`);
            this.logger.log(`Creati: ${created}, Aggiornati: ${updated}`);
            this.logger.log(`Watched: ${watchedInFile}, Watchlist: ${watchlistInFile}`);
            return { created, updated, watchedInFile, watchlistInFile };
        }
        catch (error) {
            this.logger.error(`Errore batch: ${error.message}`);
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
            }));
            this.logger.debug(`📚 Recuperati ${movies.length} film per utente ${userId}`);
            return movies;
        }
        catch (error) {
            this.logger.error(`Errore getUserMovies: ${error.message}`);
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
                ? ratingsWithValues.reduce((a, b) => a + b, 0) / ratingsWithValues.length
                : undefined;
            const lastImportDate = allUserMovies.length > 0
                ? allUserMovies.reduce((latest, current) => current.createdAt > latest ? current.createdAt : latest, allUserMovies[0].createdAt)
                : undefined;
            return {
                totalMovies: watched + watchlist,
                watchedCount: watched,
                watchlistCount: watchlist,
                averageRating,
                lastImportDate,
            };
        }
        catch (error) {
            this.logger.error(`Errore getUserMovieStats: ${error.message}`);
            throw error;
        }
    }
    async removeUserMovie(userId, movieId) {
        try {
            await this.userMovieRepository.delete({ userId, movieId });
            this.logger.debug(`🗑️ Rimosso: user ${userId} - movie ${movieId}`);
        }
        catch (error) {
            this.logger.error(`Errore removeUserMovie: ${error.message}`);
            throw error;
        }
    }
    async updateUserRating(userId, movieId, rating) {
        try {
            const userMovie = await this.userMovieRepository.findOne({
                where: { userId, movieId },
            });
            if (!userMovie) {
                throw new Error('User movie not found');
            }
            userMovie.userRating = rating;
            return await this.userMovieRepository.save(userMovie);
        }
        catch (error) {
            this.logger.error(`Errore updateUserRating: ${error.message}`);
            throw error;
        }
    }
};
exports.UserMoviesService = UserMoviesService;
exports.UserMoviesService = UserMoviesService = UserMoviesService_1 = __decorate([
    (0, common_1.Injectable)(),
    __param(0, (0, typeorm_1.InjectRepository)(user_movie_entity_1.UserMovieEntity)),
    __param(1, (0, typeorm_1.InjectRepository)(movie_entity_1.MovieEntity)),
    __metadata("design:paramtypes", [typeorm_2.Repository,
        typeorm_2.Repository])
], UserMoviesService);
//# sourceMappingURL=user-movies.service.js.map