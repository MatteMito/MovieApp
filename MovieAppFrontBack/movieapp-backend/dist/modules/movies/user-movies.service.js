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
    async associateMoviesToUser(userId, movies, status) {
        try {
            this.logger.log(`associazione ${movies.length} film (${status}) a user ${userId}`);
            const movieStatus = status === 'watched' ? user_movie_entity_1.MovieStatus.WATCHED : user_movie_entity_1.MovieStatus.WATCHLIST;
            let created = 0;
            let updated = 0;
            let skipped = 0;
            for (const movie of movies) {
                try {
                    const movieExists = await this.movieRepository.findOne({
                        where: { id: movie.id },
                    });
                    if (!movieExists) {
                        this.logger.warn(`film ${movie.title} (${movie.id}) non trovato in tabella movies, skip`);
                        skipped++;
                        continue;
                    }
                    const existing = await this.userMovieRepository.findOne({
                        where: { userId, movieId: movie.id },
                    });
                    if (existing) {
                        if (existing.status !== movieStatus) {
                            existing.status = movieStatus;
                            existing.userRating = movie.user_rating || existing.userRating;
                            existing.watchedDate = movie.watched_date
                                ? new Date(movie.watched_date)
                                : existing.watchedDate;
                            await this.userMovieRepository.save(existing);
                            updated++;
                            this.logger.log(`aggiornato: ${movie.title} (${existing.status} -> ${movieStatus})`);
                        }
                        else {
                            skipped++;
                        }
                    }
                    else {
                        const userMovie = this.userMovieRepository.create({
                            userId,
                            movieId: movie.id,
                            status: movieStatus,
                            userRating: movie.user_rating,
                            watchedDate: movie.watched_date ? new Date(movie.watched_date) : null,
                        });
                        await this.userMovieRepository.save(userMovie);
                        created++;
                    }
                }
                catch (error) {
                    this.logger.error(`errore processing film ${movie.title}: ${error.message}`);
                    skipped++;
                }
            }
            this.logger.log(`=== associazione completata ===`);
            this.logger.log(`creati: ${created}`);
            this.logger.log(`aggiornati: ${updated}`);
            this.logger.log(`skippati (duplicati): ${skipped}`);
            this.logger.log(`totale: ${created + updated}/${movies.length} film associati`);
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
            const queryBuilder = this.userMovieRepository
                .createQueryBuilder('um')
                .leftJoinAndSelect('um.movie', 'movie')
                .where('um.userId = :userId', { userId });
            if (status) {
                queryBuilder.andWhere('um.status = :status', { status });
            }
            const userMovies = await queryBuilder.getMany();
            return userMovies
                .filter(um => um.movie)
                .map(um => this.userMovieToMovie(um));
        }
        catch (error) {
            this.logger.error(`errore getUserMovies: ${error.message}`);
            throw error;
        }
    }
    async getUserMovieStats(userId) {
        try {
            const [total, watched, watchlist] = await Promise.all([
                this.userMovieRepository.count({ where: { userId } }),
                this.userMovieRepository.count({
                    where: { userId, status: user_movie_entity_1.MovieStatus.WATCHED },
                }),
                this.userMovieRepository.count({
                    where: { userId, status: user_movie_entity_1.MovieStatus.WATCHLIST },
                }),
            ]);
            const ratedMovies = await this.userMovieRepository.find({
                where: { userId, status: user_movie_entity_1.MovieStatus.WATCHED },
                select: ['userRating'],
            });
            const ratingsSum = ratedMovies
                .filter(um => um.userRating !== null && um.userRating !== undefined)
                .reduce((sum, um) => sum + um.userRating, 0);
            const ratedCount = ratedMovies.filter(um => um.userRating !== null && um.userRating !== undefined).length;
            const averageRating = ratedCount > 0 ? ratingsSum / ratedCount : 0;
            return {
                userId,
                totalMovies: total,
                watchedCount: watched,
                watchlistCount: watchlist,
                averageRating: Math.round(averageRating * 10) / 10,
                watched,
                watchlist,
                total,
            };
        }
        catch (error) {
            this.logger.error(`errore getUserMovieStats: ${error.message}`);
            throw error;
        }
    }
    userMovieToMovie(userMovie) {
        const movie = userMovie.movie;
        return {
            id: movie.id,
            title: movie.title,
            year: movie.year,
            source: movie.source,
            tmdb_id: movie.tmdb_id,
            is_enriched: movie.is_enriched,
            genres: movie.genres,
            director: movie.director,
            actors: movie.actors,
            overview: movie.overview,
            tagline: movie.tagline,
            runtime: movie.runtime,
            poster_url: movie.poster_url,
            backdrop_url: movie.backdrop_url,
            tmdb_rating: movie.tmdb_rating,
            vote_count: movie.vote_count,
            popularity: movie.popularity,
            budget: movie.budget,
            revenue: movie.revenue,
            status: userMovie.status,
            production_companies: movie.production_companies,
            production_countries: movie.production_countries,
            original_language: movie.original_language,
            original_title: movie.original_title,
            spoken_languages: movie.spoken_languages,
            adult: movie.adult,
            homepage: movie.homepage,
            imdb_id: movie.imdb_id,
            keywords: movie.keywords,
            certification: movie.certification,
            trailer_url: movie.trailer_url,
            user_rating: userMovie.userRating,
            watched_date: userMovie.watchedDate,
            user_review: userMovie.userReview,
            is_favorite: userMovie.isFavorite,
            created_at: movie.created_at,
            updated_at: movie.updated_at,
        };
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