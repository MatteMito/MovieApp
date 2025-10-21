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
let AnalyticsService = AnalyticsService_1 = class AnalyticsService {
    constructor(movieRepository) {
        this.movieRepository = movieRepository;
        this.logger = new common_1.Logger(AnalyticsService_1.name);
    }
    async getBasicStats() {
        try {
            this.logger.log('generazione statistiche base');
            const allMovies = await this.movieRepository.find();
            const watchedMovies = allMovies.filter((m) => m.is_watched);
            const enrichedMovies = allMovies.filter((m) => m.tmdb_id && m.tmdb_id > 0);
            const ratingsWithValues = allMovies
                .map((m) => m.user_rating)
                .filter((r) => r !== null && r !== undefined);
            const averageRating = ratingsWithValues.length > 0
                ? ratingsWithValues.reduce((a, b) => a + b, 0) /
                    ratingsWithValues.length
                : undefined;
            const totalRuntime = watchedMovies
                .map((m) => m.runtime || 0)
                .reduce((a, b) => a + b, 0);
            const allGenres = new Set(allMovies.flatMap((m) => m.genres || []).filter((g) => g));
            const allDirectors = new Set(allMovies.map((m) => m.director).filter((d) => d));
            const stats = {
                totalMovies: allMovies.length,
                watchedMovies: watchedMovies.length,
                watchlistMovies: allMovies.length - watchedMovies.length,
                enrichedMovies: enrichedMovies.length,
                averageRating,
                totalRuntime,
                uniqueGenres: allGenres.size,
                uniqueDirectors: allDirectors.size,
            };
            this.logger.log(`stats generate: ${stats.totalMovies} film`);
            return stats;
        }
        catch (error) {
            this.logger.error(`errore stats base: ${error.message}`);
            throw error;
        }
    }
    async getGenreStats(limit = 10) {
        try {
            this.logger.log(`generazione stats generi (top ${limit})`);
            const allMovies = await this.movieRepository.find();
            const totalMovies = allMovies.length;
            const genreCounts = new Map();
            const genreRatings = new Map();
            allMovies.forEach((movie) => {
                movie.genres?.forEach((genre) => {
                    if (genre) {
                        genreCounts.set(genre, (genreCounts.get(genre) || 0) + 1);
                        if (movie.user_rating) {
                            const ratings = genreRatings.get(genre) || [];
                            ratings.push(movie.user_rating);
                            genreRatings.set(genre, ratings);
                        }
                    }
                });
            });
            const genreStats = Array.from(genreCounts.entries())
                .map(([genre, count]) => {
                const ratings = genreRatings.get(genre) || [];
                const averageRating = ratings.length > 0
                    ? ratings.reduce((a, b) => a + b, 0) / ratings.length
                    : undefined;
                return {
                    genre,
                    count,
                    percentage: (count / totalMovies) * 100,
                    averageRating,
                };
            })
                .sort((a, b) => b.count - a.count)
                .slice(0, limit);
            this.logger.log(`${genreStats.length} generi generati`);
            return genreStats;
        }
        catch (error) {
            this.logger.error(`errore stats generi: ${error.message}`);
            throw error;
        }
    }
    async getYearStats() {
        try {
            this.logger.log('generazione stats anni');
            const allMovies = await this.movieRepository.find();
            const moviesWithYear = allMovies.filter((m) => m.year && m.year > 1900 && m.year <= new Date().getFullYear());
            const yearCounts = new Map();
            const yearRatings = new Map();
            moviesWithYear.forEach((movie) => {
                const year = movie.year;
                yearCounts.set(year, (yearCounts.get(year) || 0) + 1);
                if (movie.user_rating) {
                    const ratings = yearRatings.get(year) || [];
                    ratings.push(movie.user_rating);
                    yearRatings.set(year, ratings);
                }
            });
            const yearStats = Array.from(yearCounts.entries())
                .map(([year, count]) => {
                const ratings = yearRatings.get(year) || [];
                const averageRating = ratings.length > 0
                    ? ratings.reduce((a, b) => a + b, 0) / ratings.length
                    : undefined;
                return {
                    year,
                    count,
                    averageRating,
                };
            })
                .sort((a, b) => a.year - b.year);
            this.logger.log(`${yearStats.length} anni generati`);
            return yearStats;
        }
        catch (error) {
            this.logger.error(`errore stats anni: ${error.message}`);
            throw error;
        }
    }
    async getDirectorStats(limit = 10) {
        try {
            this.logger.log(`generazione stats registi (top ${limit})`);
            const allMovies = await this.movieRepository.find();
            const moviesWithDirector = allMovies.filter((m) => m.director && m.director.trim());
            const directorCounts = new Map();
            const directorRatings = new Map();
            const directorRuntimes = new Map();
            moviesWithDirector.forEach((movie) => {
                const director = movie.director;
                directorCounts.set(director, (directorCounts.get(director) || 0) + 1);
                if (movie.user_rating) {
                    const ratings = directorRatings.get(director) || [];
                    ratings.push(movie.user_rating);
                    directorRatings.set(director, ratings);
                }
                if (movie.runtime) {
                    const currentRuntime = directorRuntimes.get(director) || 0;
                    directorRuntimes.set(director, currentRuntime + movie.runtime);
                }
            });
            const directorStats = Array.from(directorCounts.entries())
                .map(([director, movieCount]) => {
                const ratings = directorRatings.get(director) || [];
                const averageRating = ratings.length > 0
                    ? ratings.reduce((a, b) => a + b, 0) / ratings.length
                    : undefined;
                return {
                    director,
                    movieCount,
                    averageRating,
                    totalRuntime: directorRuntimes.get(director) || 0,
                };
            })
                .sort((a, b) => b.movieCount - a.movieCount)
                .slice(0, limit);
            this.logger.log(`${directorStats.length} registi generati`);
            return directorStats;
        }
        catch (error) {
            this.logger.error(`errore stats registi: ${error.message}`);
            throw error;
        }
    }
    async getRatingDistribution() {
        try {
            this.logger.log('generazione distribuzione rating');
            const allMovies = await this.movieRepository.find();
            const distribution = {};
            for (let i = 1; i <= 10; i++) {
                distribution[i.toString()] = 0;
            }
            allMovies.forEach((movie) => {
                if (movie.user_rating) {
                    const rating = Math.round(movie.user_rating);
                    if (rating >= 1 && rating <= 10) {
                        distribution[rating.toString()]++;
                    }
                }
            });
            this.logger.log('distribuzione rating generata');
            return distribution;
        }
        catch (error) {
            this.logger.error(`errore distribuzione rating: ${error.message}`);
            throw error;
        }
    }
    async getDecadeDistribution() {
        try {
            this.logger.log('generazione distribuzione decadi');
            const allMovies = await this.movieRepository.find();
            const distribution = {};
            allMovies.forEach((movie) => {
                if (movie.year && movie.year >= 1900) {
                    const decade = Math.floor(movie.year / 10) * 10;
                    const key = `${decade}s`;
                    distribution[key] = (distribution[key] || 0) + 1;
                }
            });
            this.logger.log('distribuzione decadi generata');
            return distribution;
        }
        catch (error) {
            this.logger.error(`errore distribuzione decadi: ${error.message}`);
            throw error;
        }
    }
    async getAdvancedAnalytics() {
        try {
            this.logger.log('=== generazione analytics avanzate ===');
            const [basic, topGenres, moviesByYear, topDirectors, ratingDistribution, decadeDistribution,] = await Promise.all([
                this.getBasicStats(),
                this.getGenreStats(10),
                this.getYearStats(),
                this.getDirectorStats(10),
                this.getRatingDistribution(),
                this.getDecadeDistribution(),
            ]);
            this.logger.log('✅ analytics avanzate complete');
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
    async generateTextReport() {
        try {
            const analytics = await this.getAdvancedAnalytics();
            const report = [
                '=== MOVIEAPP ANALYTICS REPORT ===',
                '',
                '📊 STATISTICHE BASE',
                `film totali: ${analytics.basic.totalMovies}`,
                `visti: ${analytics.basic.watchedMovies}`,
                `da vedere: ${analytics.basic.watchlistMovies}`,
                `arricchiti: ${analytics.basic.enrichedMovies}`,
                analytics.basic.averageRating
                    ? `rating medio: ${analytics.basic.averageRating.toFixed(1)}/10`
                    : '',
                `runtime totale: ${Math.round(analytics.basic.totalRuntime / 60)}h`,
                `generi unici: ${analytics.basic.uniqueGenres}`,
                `registi unici: ${analytics.basic.uniqueDirectors}`,
                '',
                '🎭 TOP 5 GENERI',
                ...analytics.topGenres.slice(0, 5).map((g, i) => {
                    const rating = g.averageRating
                        ? ` (avg: ${g.averageRating.toFixed(1)})`
                        : '';
                    return `${i + 1}. ${g.genre}: ${g.count} film (${g.percentage.toFixed(1)}%)${rating}`;
                }),
                '',
                '🎬 TOP 5 REGISTI',
                ...analytics.topDirectors.slice(0, 5).map((d, i) => {
                    const rating = d.averageRating
                        ? ` (avg: ${d.averageRating.toFixed(1)})`
                        : '';
                    return `${i + 1}. ${d.director}: ${d.movieCount} film${rating}`;
                }),
                '',
                '📅 DISTRIBUZIONE DECADI',
                ...Object.entries(analytics.decadeDistribution)
                    .sort((a, b) => a[0].localeCompare(b[0]))
                    .map(([decade, count]) => `${decade}: ${count} film`),
            ]
                .filter((line) => line !== '')
                .join('\n');
            this.logger.log('report testuale generato');
            return report;
        }
        catch (error) {
            this.logger.error(`errore report: ${error.message}`);
            throw error;
        }
    }
};
exports.AnalyticsService = AnalyticsService;
exports.AnalyticsService = AnalyticsService = AnalyticsService_1 = __decorate([
    (0, common_1.Injectable)(),
    __param(0, (0, typeorm_1.InjectRepository)(movie_entity_1.MovieEntity)),
    __metadata("design:paramtypes", [typeorm_2.Repository])
], AnalyticsService);
//# sourceMappingURL=analytics.service.js.map