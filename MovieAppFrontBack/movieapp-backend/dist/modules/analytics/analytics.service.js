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
var AnalyticsService_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.AnalyticsService = void 0;
const common_1 = require("@nestjs/common");
const typeorm_1 = require("@nestjs/typeorm");
const typeorm_2 = require("typeorm");
const movie_entity_1 = require("../../database/entities/movie.entity");
const user_movie_entity_1 = require("../../database/entities/user-movie.entity");
let AnalyticsService = AnalyticsService_1 = class AnalyticsService {
    constructor(movieRepository, userMovieRepository) {
        this.movieRepository = movieRepository;
        this.userMovieRepository = userMovieRepository;
        this.logger = new common_1.Logger(AnalyticsService_1.name);
    }
    async getBasicStats(userId) {
        try {
            this.logger.log(`generazione statistiche base per utente ${userId}`);
            const userMovies = await this.userMovieRepository.find({
                where: { userId },
                relations: ['movie'],
            });
            const allMovies = userMovies.map(um => um.movie);
            const watchedMovies = userMovies.filter(um => um.status === user_movie_entity_1.MovieStatus.WATCHED);
            const enrichedMovies = allMovies.filter(m => m.tmdb_id && m.tmdb_id > 0);
            const ratingsWithValues = userMovies
                .map(um => um.userRating)
                .filter((r) => r !== null && r !== undefined);
            const averageRating = ratingsWithValues.length > 0
                ? ratingsWithValues.reduce((a, b) => a + b, 0) / ratingsWithValues.length
                : undefined;
            const totalRuntime = allMovies
                .map(m => m.runtime || 0)
                .reduce((a, b) => a + b, 0);
            const allGenres = new Set();
            allMovies.forEach(m => {
                m.genres?.forEach(g => allGenres.add(g));
            });
            const allDirectors = new Set();
            allMovies.forEach(m => {
                if (m.director)
                    allDirectors.add(m.director);
            });
            return {
                totalMovies: allMovies.length,
                watchedMovies: watchedMovies.length,
                watchlistMovies: userMovies.filter(um => um.status === user_movie_entity_1.MovieStatus.WATCHLIST).length,
                enrichedMovies: enrichedMovies.length,
                averageRating,
                totalRuntime,
                uniqueGenres: allGenres.size,
                uniqueDirectors: allDirectors.size,
            };
        }
        catch (error) {
            this.logger.error(`errore statistiche base: ${error.message}`);
            throw error;
        }
    }
    async getGenreStats(userId) {
        try {
            const userMovies = await this.userMovieRepository.find({
                where: { userId },
                relations: ['movie'],
            });
            const genreMap = new Map();
            userMovies.forEach(um => {
                um.movie.genres?.forEach(genre => {
                    if (!genreMap.has(genre)) {
                        genreMap.set(genre, { count: 0, ratings: [] });
                    }
                    const stats = genreMap.get(genre);
                    stats.count++;
                    if (um.userRating) {
                        stats.ratings.push(um.userRating);
                    }
                });
            });
            const totalMovies = userMovies.length;
            const genreStats = Array.from(genreMap.entries())
                .map(([genre, stats]) => ({
                genre,
                count: stats.count,
                percentage: (stats.count / totalMovies) * 100,
                averageRating: stats.ratings.length > 0
                    ? stats.ratings.reduce((a, b) => a + b, 0) / stats.ratings.length
                    : undefined,
            }))
                .sort((a, b) => b.count - a.count)
                .slice(0, 10);
            return genreStats;
        }
        catch (error) {
            this.logger.error(`errore statistiche generi: ${error.message}`);
            return [];
        }
    }
    async getYearStats(userId) {
        try {
            const userMovies = await this.userMovieRepository.find({
                where: { userId },
                relations: ['movie'],
            });
            const yearMap = new Map();
            userMovies.forEach(um => {
                const year = um.movie.year;
                if (!year)
                    return;
                if (!yearMap.has(year)) {
                    yearMap.set(year, { count: 0, ratings: [] });
                }
                const stats = yearMap.get(year);
                stats.count++;
                if (um.userRating) {
                    stats.ratings.push(um.userRating);
                }
            });
            const yearStats = Array.from(yearMap.entries())
                .map(([year, stats]) => ({
                year,
                count: stats.count,
                averageRating: stats.ratings.length > 0
                    ? stats.ratings.reduce((a, b) => a + b, 0) / stats.ratings.length
                    : undefined,
            }))
                .sort((a, b) => b.year - a.year);
            return yearStats;
        }
        catch (error) {
            this.logger.error(`errore statistiche anni: ${error.message}`);
            return [];
        }
    }
    async getDirectorStats(userId) {
        try {
            const userMovies = await this.userMovieRepository.find({
                where: { userId },
                relations: ['movie'],
            });
            const directorMap = new Map();
            userMovies.forEach(um => {
                const director = um.movie.director;
                if (!director)
                    return;
                if (!directorMap.has(director)) {
                    directorMap.set(director, { count: 0, ratings: [], runtime: 0 });
                }
                const stats = directorMap.get(director);
                stats.count++;
                if (um.userRating) {
                    stats.ratings.push(um.userRating);
                }
                stats.runtime += um.movie.runtime || 0;
            });
            const directorStats = Array.from(directorMap.entries())
                .map(([director, stats]) => ({
                director,
                movieCount: stats.count,
                averageRating: stats.ratings.length > 0
                    ? stats.ratings.reduce((a, b) => a + b, 0) / stats.ratings.length
                    : undefined,
                totalRuntime: stats.runtime,
            }))
                .sort((a, b) => b.movieCount - a.movieCount)
                .slice(0, 10);
            return directorStats;
        }
        catch (error) {
            this.logger.error(`errore statistiche registi: ${error.message}`);
            return [];
        }
    }
    async getAdvancedAnalytics(userId) {
        try {
            this.logger.log(`generazione analytics avanzate per utente ${userId}`);
            const [basic, topGenres, moviesByYear, topDirectors] = await Promise.all([
                this.getBasicStats(userId),
                this.getGenreStats(userId),
                this.getYearStats(userId),
                this.getDirectorStats(userId),
            ]);
            const userMovies = await this.userMovieRepository.find({
                where: { userId },
                relations: ['movie'],
            });
            const ratingDistribution = {};
            userMovies.forEach(um => {
                if (um.userRating) {
                    const rating = Math.round(um.userRating);
                    ratingDistribution[rating] = (ratingDistribution[rating] || 0) + 1;
                }
            });
            const decadeDistribution = {};
            userMovies.forEach(um => {
                if (um.movie.year) {
                    const decade = Math.floor(um.movie.year / 10) * 10;
                    decadeDistribution[decade] = (decadeDistribution[decade] || 0) + 1;
                }
            });
            return {
                basic,
                topGenres,
                moviesByYear,
                topDirectors,
                ratingDistribution,
                decadeDistribution,
            };
        }
        catch (error) {
            this.logger.error(`errore analytics avanzate: ${error.message}`);
            throw error;
        }
    }
};
exports.AnalyticsService = AnalyticsService;
exports.AnalyticsService = AnalyticsService = AnalyticsService_1 = __decorate([
    (0, common_1.Injectable)(),
    __param(0, (0, typeorm_1.InjectRepository)(movie_entity_1.MovieEntity)),
    __param(1, (0, typeorm_1.InjectRepository)(user_movie_entity_1.UserMovieEntity)),
    __metadata("design:paramtypes", [typeorm_2.Repository,
        typeorm_2.Repository])
], AnalyticsService);
//# sourceMappingURL=analytics.service.js.map