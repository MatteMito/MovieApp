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
                this.logger.debug(`🔄 Aggiornamento associazione: user ${userId} - movie ${movieId}`);
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
                this.logger.debug(`➕ Nuova associazione: user ${userId} - movie ${movieId} [${status}]`);
            }
            return await this.userMovieRepository.save(userMovie);
        }
        catch (error) {
            this.logger.error(`Errore associazione film-utente: ${error.message}`);
            throw error;
        }
    }
    async batchAssociateMoviesToUser(userId, movies) {
        try {
            this.logger.log(`📦 Batch associazione: ${movies.length} film per utente ${userId}`);
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
                this.logger.log(`✅ Chunk ${Math.floor(i / CHUNK_SIZE) + 1}: processati ${chunk.length} film`);
            }
            this.logger.log(`=== BATCH COMPLETATO ===`);
            this.logger.log(`Creati: ${created}`);
            this.logger.log(`Aggiornati: ${updated}`);
            this.logger.log(`Watched nel file: ${watchedInFile}`);
            this.logger.log(`Watchlist nel file: ${watchlistInFile}`);
            return { created, updated, watchedInFile, watchlistInFile };
        }
        catch (error) {
            this.logger.error(`Errore batch associazione: ${error.message}`);
            throw error;
        }
    }
    async getUserMovies(userId, status) {
        try {
            const whereClause = { userId };
            if (status) {
                whereClause.status = status;
            }
            const userMovies = await this.userMovieRepository.find({
                where: whereClause,
                relations: ['movie'],
                order: { createdAt: 'DESC' },
            });
            this.logger.debug(`📚 Recuperati ${userMovies.length} film per utente ${userId}`);
            return userMovies;
        }
        catch (error) {
            this.logger.error(`Errore recupero film utente: ${error.message}`);
            return [];
        }
    }
    async getUserMovieStats(userId) {
        try {
            const [totalMovies, watchedCount, watchlistCount] = await Promise.all([
                this.userMovieRepository.count({ where: { userId } }),
                this.userMovieRepository.count({ where: { userId, status: user_movie_entity_1.MovieStatus.WATCHED } }),
                this.userMovieRepository.count({ where: { userId, status: user_movie_entity_1.MovieStatus.WATCHLIST } }),
            ]);
            const userMoviesWithRating = await this.userMovieRepository.find({
                where: { userId },
                select: ['userRating'],
            });
            const ratings = userMoviesWithRating
                .filter((um) => um.userRating !== null && um.userRating !== undefined)
                .map((um) => um.userRating);
            const averageRating = ratings.length > 0
                ? ratings.reduce((sum, rating) => sum + rating, 0) / ratings.length
                : undefined;
            const lastImport = await this.userMovieRepository.findOne({
                where: { userId },
                order: { createdAt: 'DESC' },
                select: ['createdAt'],
            });
            const stats = {
                totalMovies,
                watchedCount,
                watchlistCount,
                averageRating,
                lastImportDate: lastImport?.createdAt,
            };
            this.logger.debug(`📊 Statistiche utente ${userId}:`, stats);
            return stats;
        }
        catch (error) {
            this.logger.error(`Errore statistiche utente: ${error.message}`);
            return {
                totalMovies: 0,
                watchedCount: 0,
                watchlistCount: 0,
            };
        }
    }
    async removeMovieFromUser(userId, movieId) {
        try {
            const result = await this.userMovieRepository.delete({ userId, movieId });
            if (result.affected > 0) {
                this.logger.log(`🗑️ Rimossa associazione: user ${userId} - movie ${movieId}`);
            }
            else {
                this.logger.warn(`⚠️ Associazione non trovata: user ${userId} - movie ${movieId}`);
            }
        }
        catch (error) {
            this.logger.error(`Errore rimozione associazione: ${error.message}`);
            throw error;
        }
    }
    async removeAllUserMovies(userId) {
        try {
            const result = await this.userMovieRepository.delete({ userId });
            const deletedCount = result.affected || 0;
            this.logger.log(`🗑️ Rimossi ${deletedCount} film per utente ${userId}`);
            return deletedCount;
        }
        catch (error) {
            this.logger.error(`Errore rimozione film utente: ${error.message}`);
            throw error;
        }
    }
    async userHasMovie(userId, movieId) {
        try {
            const count = await this.userMovieRepository.count({
                where: { userId, movieId },
            });
            return count > 0;
        }
        catch (error) {
            this.logger.error(`Errore verifica film utente: ${error.message}`);
            return false;
        }
    }
    async getUserMovieIds(userId, status) {
        try {
            const whereClause = { userId };
            if (status) {
                whereClause.status = status;
            }
            const userMovies = await this.userMovieRepository.find({
                where: whereClause,
                select: ['movieId'],
            });
            return userMovies.map((um) => um.movieId);
        }
        catch (error) {
            this.logger.error(`Errore recupero movie IDs: ${error.message}`);
            return [];
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