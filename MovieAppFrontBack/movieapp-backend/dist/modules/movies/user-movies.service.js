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
                        if (existing.status === user_movie_entity_1.MovieStatus.WATCHED && movieStatus === user_movie_entity_1.MovieStatus.WATCHLIST) {
                            this.logger.debug(`film ${movie.title} già WATCHED, skip aggiornamento a WATCHLIST`);
                            skipped++;
                            continue;
                        }
                        if (existing.status !== movieStatus) {
                            existing.status = movieStatus;
                            existing.userRating = movie.user_rating || existing.userRating;
                            existing.watchedDate = movie.watched_date
                                ? new Date(movie.watched_date)
                                : existing.watchedDate;
                            await this.userMovieRepository.save(existing);
                            updated++;
                            this.logger.debug(`aggiornato ${movie.title} da ${existing.status} a ${movieStatus}`);
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
                        if (created <= 5) {
                            this.logger.debug(`creato: ${movie.title} (${movieStatus})`);
                        }
                    }
                }
                catch (error) {
                    this.logger.error(`errore associazione film ${movie.title}:`, error);
                    skipped++;
                }
            }
            this.logger.log(`associazione completata:`);
            this.logger.log(`  creati: ${created}`);
            this.logger.log(`  aggiornati: ${updated}`);
            this.logger.log(`  skippati: ${skipped}`);
        }
        catch (error) {
            this.logger.error('errore associazione batch movies:', error);
            throw error;
        }
    }
    async getUserMovieStats(userId) {
        try {
            const allMovies = await this.userMovieRepository.find({
                where: { userId },
            });
            const watched = allMovies.filter(m => m.status === user_movie_entity_1.MovieStatus.WATCHED).length;
            const watchlist = allMovies.filter(m => m.status === user_movie_entity_1.MovieStatus.WATCHLIST).length;
            const ratingsSum = allMovies
                .filter(m => m.userRating !== null && m.userRating !== undefined)
                .reduce((sum, m) => sum + (m.userRating || 0), 0);
            const ratingsCount = allMovies.filter(m => m.userRating).length;
            const averageRating = ratingsCount > 0 ? ratingsSum / ratingsCount : 0;
            return {
                userId,
                totalMovies: allMovies.length,
                watchedCount: watched,
                watchlistCount: watchlist,
                averageRating: Math.round(averageRating * 10) / 10,
                watched,
                watchlist,
                total: allMovies.length,
            };
        }
        catch (error) {
            this.logger.error(`errore recupero stats user ${userId}:`, error);
            throw error;
        }
    }
    async getUserMovies(userId, status) {
        try {
            const queryBuilder = this.userMovieRepository
                .createQueryBuilder('userMovie')
                .leftJoinAndSelect('userMovie.movie', 'movie')
                .where('userMovie.userId = :userId', { userId });
            if (status) {
                const movieStatus = status === 'watched' ? user_movie_entity_1.MovieStatus.WATCHED : user_movie_entity_1.MovieStatus.WATCHLIST;
                queryBuilder.andWhere('userMovie.status = :status', { status: movieStatus });
            }
            const userMovies = await queryBuilder.getMany();
            return userMovies.map(um => ({
                ...um.movie,
                user_rating: um.userRating,
                watched_date: um.watchedDate?.toISOString(),
                status: um.status === user_movie_entity_1.MovieStatus.WATCHED ? 'watched' : 'watchlist',
            }));
        }
        catch (error) {
            this.logger.error(`errore recupero movies user ${userId}:`, error);
            throw error;
        }
    }
    async deleteUserMovie(userId, movieId) {
        try {
            await this.userMovieRepository.delete({ userId, movieId });
            this.logger.log(`eliminato film ${movieId} per user ${userId}`);
        }
        catch (error) {
            this.logger.error(`errore eliminazione film ${movieId}:`, error);
            throw error;
        }
    }
    async deleteAllUserMovies(userId) {
        try {
            await this.userMovieRepository.delete({ userId });
            this.logger.log(`eliminati tutti i film per user ${userId}`);
        }
        catch (error) {
            this.logger.error(`errore eliminazione film user ${userId}:`, error);
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