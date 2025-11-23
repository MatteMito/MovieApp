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
    async getCompleteAnalytics(userId) {
        this.logger.log(`generazione analytics complete per utente ${userId}`);
        const watchedMovies = await this.userMovieRepository.find({
            where: { userId, status: user_movie_entity_1.MovieStatus.WATCHED },
            relations: ['movie'],
        });
        const ratedMovies = await this.userMovieRepository.find({
            where: {
                userId,
                status: user_movie_entity_1.MovieStatus.WATCHED,
                userRating: (0, typeorm_2.Not)((0, typeorm_2.IsNull)())
            },
            relations: ['movie'],
        });
        this.logger.log(`watched: ${watchedMovies.length}, rated: ${ratedMovies.length}`);
        const [basicStats, watchedStats, ratingStats] = await Promise.all([
            this.generateBasicStats(userId, watchedMovies),
            this.generateWatchedStats(watchedMovies),
            this.generateRatingStats(ratedMovies),
        ]);
        return {
            basicStats,
            watchedStats,
            ratingStats,
        };
    }
    async generateBasicStats(userId, watchedMovies) {
        const allUserMovies = await this.userMovieRepository.find({
            where: { userId },
            relations: ['movie'],
        });
        const watchlistMovies = allUserMovies.filter(um => um.status === user_movie_entity_1.MovieStatus.WATCHLIST);
        const allMovies = allUserMovies.map(um => um.movie);
        const enrichedMovies = allMovies.filter(m => m.tmdb_id && m.tmdb_id > 0);
        const watchedWithRating = watchedMovies.filter(um => um.userRating != null);
        const averageRating = watchedWithRating.length > 0
            ? watchedWithRating.reduce((sum, um) => sum + um.userRating, 0) / watchedWithRating.length
            : undefined;
        const totalRuntime = watchedMovies
            .map(um => um.movie.runtime || 0)
            .reduce((a, b) => a + b, 0);
        const allGenres = new Set();
        watchedMovies.forEach(um => {
            um.movie.genres?.forEach(g => allGenres.add(g));
        });
        const allDirectors = new Set();
        watchedMovies.forEach(um => {
            if (um.movie.director)
                allDirectors.add(um.movie.director);
        });
        return {
            totalMovies: allUserMovies.length,
            watchedCount: watchedMovies.length,
            watchlistCount: watchlistMovies.length,
            enrichedMovies: enrichedMovies.length,
            averageRating: averageRating ? parseFloat(averageRating.toFixed(2)) : undefined,
            totalRuntime,
            uniqueGenres: allGenres.size,
            uniqueDirectors: allDirectors.size,
        };
    }
    async generateWatchedStats(watchedMovies) {
        const totalWatched = watchedMovies.length;
        const genreMap = new Map();
        watchedMovies.forEach(um => {
            um.movie.genres?.forEach(genre => {
                if (!genreMap.has(genre)) {
                    genreMap.set(genre, { count: 0, ratings: [] });
                }
                const stats = genreMap.get(genre);
                stats.count++;
                if (um.userRating)
                    stats.ratings.push(um.userRating);
            });
        });
        const genresDistribution = Array.from(genreMap.entries())
            .map(([genre, stats]) => ({
            genre,
            count: stats.count,
            percentage: (stats.count / totalWatched) * 100,
            averageRating: stats.ratings.length > 0
                ? stats.ratings.reduce((a, b) => a + b) / stats.ratings.length
                : undefined,
        }))
            .sort((a, b) => b.count - a.count);
        const genreCombinations = {};
        watchedMovies.forEach(um => {
            const genres = um.movie.genres || [];
            if (genres.length >= 2) {
                for (let i = 0; i < genres.length; i++) {
                    for (let j = i + 1; j < genres.length; j++) {
                        const combo = [genres[i], genres[j]].sort().join(' + ');
                        genreCombinations[combo] = (genreCombinations[combo] || 0) + 1;
                    }
                }
            }
        });
        const decadeDistribution = {};
        watchedMovies.forEach(um => {
            if (um.movie.year) {
                const decade = `${Math.floor(um.movie.year / 10) * 10}s`;
                decadeDistribution[decade] = (decadeDistribution[decade] || 0) + 1;
            }
        });
        const directorMap = new Map();
        watchedMovies.forEach(um => {
            if (um.movie.director) {
                if (!directorMap.has(um.movie.director)) {
                    directorMap.set(um.movie.director, { count: 0, ratings: [], runtime: 0 });
                }
                const stats = directorMap.get(um.movie.director);
                stats.count++;
                if (um.userRating)
                    stats.ratings.push(um.userRating);
                stats.runtime += um.movie.runtime || 0;
            }
        });
        const topDirectors = Array.from(directorMap.entries())
            .map(([director, stats]) => ({
            director,
            movieCount: stats.count,
            averageRating: stats.ratings.length > 0
                ? stats.ratings.reduce((a, b) => a + b) / stats.ratings.length
                : undefined,
            totalRuntime: stats.runtime,
        }))
            .sort((a, b) => b.movieCount - a.movieCount)
            .slice(0, 10);
        const actorMap = new Map();
        watchedMovies.forEach(um => {
            um.movie.actors?.forEach(actor => {
                if (!actorMap.has(actor)) {
                    actorMap.set(actor, { count: 0, ratings: [] });
                }
                const stats = actorMap.get(actor);
                stats.count++;
                if (um.userRating)
                    stats.ratings.push(um.userRating);
            });
        });
        const topActors = Array.from(actorMap.entries())
            .map(([actor, stats]) => ({
            actor,
            movieCount: stats.count,
            averageRating: stats.ratings.length > 0
                ? stats.ratings.reduce((a, b) => a + b) / stats.ratings.length
                : undefined,
        }))
            .sort((a, b) => b.movieCount - a.movieCount)
            .slice(0, 10);
        const pairMap = new Map();
        watchedMovies.forEach(um => {
            if (um.movie.director && um.movie.actors) {
                um.movie.actors.forEach(actor => {
                    const pair = `${um.movie.director}|${actor}`;
                    pairMap.set(pair, (pairMap.get(pair) || 0) + 1);
                });
            }
        });
        const directorActorPairs = Array.from(pairMap.entries())
            .map(([pair, count]) => {
            const [director, actor] = pair.split('|');
            return { director, actor, movieCount: count };
        })
            .sort((a, b) => b.movieCount - a.movieCount)
            .slice(0, 10);
        const studioMap = new Map();
        watchedMovies.forEach(um => {
            um.movie.production_companies?.forEach(studio => {
                studioMap.set(studio, (studioMap.get(studio) || 0) + 1);
            });
        });
        const productionStudios = Array.from(studioMap.entries())
            .map(([studio, count]) => ({ studio, count }))
            .sort((a, b) => b.count - a.count)
            .slice(0, 10);
        const countryMap = new Map();
        watchedMovies.forEach(um => {
            um.movie.production_countries?.forEach(country => {
                countryMap.set(country, (countryMap.get(country) || 0) + 1);
            });
        });
        const productionCountries = Array.from(countryMap.entries())
            .map(([country, count]) => ({
            country,
            count,
            percentage: (count / totalWatched) * 100,
        }))
            .sort((a, b) => b.count - a.count);
        const continentDistribution = {
            'North America': 0,
            'Europe': 0,
            'Asia': 0,
            'South America': 0,
            'Africa': 0,
            'Oceania': 0,
        };
        const continentMap = {
            'United States': 'North America',
            'Canada': 'North America',
            'United Kingdom': 'Europe',
            'France': 'Europe',
            'Germany': 'Europe',
            'Italy': 'Europe',
            'Spain': 'Europe',
            'Japan': 'Asia',
            'South Korea': 'Asia',
            'China': 'Asia',
            'India': 'Asia',
            'Australia': 'Oceania',
        };
        watchedMovies.forEach(um => {
            um.movie.production_countries?.forEach(country => {
                const continent = continentMap[country] || 'Other';
                if (continentDistribution[continent] !== undefined) {
                    continentDistribution[continent]++;
                }
            });
        });
        return {
            genresDistribution,
            genreCombinations,
            decadeDistribution,
            topDirectors,
            topActors,
            directorActorPairs,
            topWriters: [],
            topComposers: [],
            topCinematographers: [],
            productionStudios,
            productionCountries,
            continentDistribution,
        };
    }
    async generateRatingStats(ratedMovies) {
        if (ratedMovies.length === 0) {
            return {
                userRatingsDistribution: {},
                communityRatingsDistribution: {},
                divergentOpinions: [],
                avgRatingByDirector: [],
                avgRatingByActor: [],
                avgRatingByGenre: [],
                avgRatingByGenreCombination: [],
                avgRatingByDecade: {},
                runtimeVsRating: [],
                revenueVsRating: [],
            };
        }
        const userRatingsDistribution = {};
        ratedMovies.forEach(um => {
            const rating = Math.round(um.userRating);
            userRatingsDistribution[rating] = (userRatingsDistribution[rating] || 0) + 1;
        });
        const communityRatingsDistribution = {};
        ratedMovies.forEach(um => {
            if (um.movie.tmdb_rating) {
                const rating = Math.round(um.movie.tmdb_rating);
                communityRatingsDistribution[rating] = (communityRatingsDistribution[rating] || 0) + 1;
            }
        });
        const divergentOpinions = ratedMovies
            .filter(um => um.movie.tmdb_rating && Math.abs(um.userRating - um.movie.tmdb_rating) >= 2)
            .map(um => ({
            movieTitle: um.movie.title,
            userRating: um.userRating,
            tmdbRating: um.movie.tmdb_rating,
            difference: um.userRating - um.movie.tmdb_rating,
        }))
            .sort((a, b) => Math.abs(b.difference) - Math.abs(a.difference))
            .slice(0, 20);
        const directorRatings = new Map();
        ratedMovies.forEach(um => {
            if (um.movie.director) {
                if (!directorRatings.has(um.movie.director)) {
                    directorRatings.set(um.movie.director, []);
                }
                directorRatings.get(um.movie.director).push(um.userRating);
            }
        });
        const avgRatingByDirector = Array.from(directorRatings.entries())
            .filter(([_, ratings]) => ratings.length >= 3)
            .map(([director, ratings]) => ({
            director,
            avgRating: ratings.reduce((a, b) => a + b) / ratings.length,
        }))
            .sort((a, b) => b.avgRating - a.avgRating)
            .slice(0, 10);
        const actorRatings = new Map();
        ratedMovies.forEach(um => {
            um.movie.actors?.forEach(actor => {
                if (!actorRatings.has(actor)) {
                    actorRatings.set(actor, []);
                }
                actorRatings.get(actor).push(um.userRating);
            });
        });
        const avgRatingByActor = Array.from(actorRatings.entries())
            .filter(([_, ratings]) => ratings.length >= 3)
            .map(([actor, ratings]) => ({
            actor,
            avgRating: ratings.reduce((a, b) => a + b) / ratings.length,
        }))
            .sort((a, b) => b.avgRating - a.avgRating)
            .slice(0, 10);
        const genreRatings = new Map();
        ratedMovies.forEach(um => {
            um.movie.genres?.forEach(genre => {
                if (!genreRatings.has(genre)) {
                    genreRatings.set(genre, []);
                }
                genreRatings.get(genre).push(um.userRating);
            });
        });
        const avgRatingByGenre = Array.from(genreRatings.entries())
            .map(([genre, ratings]) => ({
            genre,
            avgRating: ratings.reduce((a, b) => a + b) / ratings.length,
        }))
            .sort((a, b) => b.avgRating - a.avgRating);
        const comboRatings = new Map();
        ratedMovies.forEach(um => {
            const genres = um.movie.genres || [];
            if (genres.length >= 2) {
                for (let i = 0; i < genres.length; i++) {
                    for (let j = i + 1; j < genres.length; j++) {
                        const combo = [genres[i], genres[j]].sort().join(' + ');
                        if (!comboRatings.has(combo)) {
                            comboRatings.set(combo, []);
                        }
                        comboRatings.get(combo).push(um.userRating);
                    }
                }
            }
        });
        const avgRatingByGenreCombination = Array.from(comboRatings.entries())
            .filter(([_, ratings]) => ratings.length >= 3)
            .map(([combination, ratings]) => ({
            combination,
            avgRating: ratings.reduce((a, b) => a + b) / ratings.length,
        }))
            .sort((a, b) => b.avgRating - a.avgRating)
            .slice(0, 10);
        const decadeRatings = new Map();
        ratedMovies.forEach(um => {
            if (um.movie.year) {
                const decade = `${Math.floor(um.movie.year / 10) * 10}s`;
                if (!decadeRatings.has(decade)) {
                    decadeRatings.set(decade, []);
                }
                decadeRatings.get(decade).push(um.userRating);
            }
        });
        const avgRatingByDecade = {};
        decadeRatings.forEach((ratings, decade) => {
            avgRatingByDecade[decade] = ratings.reduce((a, b) => a + b) / ratings.length;
        });
        const runtimeVsRating = ratedMovies
            .filter(um => um.movie.runtime)
            .map(um => ({
            runtime: um.movie.runtime,
            rating: um.userRating,
        }));
        const revenueVsRating = ratedMovies
            .filter(um => um.movie.revenue && um.movie.revenue > 0)
            .map(um => ({
            revenue: um.movie.revenue,
            rating: um.userRating,
        }));
        return {
            userRatingsDistribution,
            communityRatingsDistribution,
            divergentOpinions,
            avgRatingByDirector,
            avgRatingByActor,
            avgRatingByGenre,
            avgRatingByGenreCombination,
            avgRatingByDecade,
            runtimeVsRating,
            revenueVsRating,
        };
    }
    async getBasicStats(userId) {
        const watchedMovies = await this.userMovieRepository.find({
            where: { userId, status: user_movie_entity_1.MovieStatus.WATCHED },
            relations: ['movie'],
        });
        return this.generateBasicStats(userId, watchedMovies);
    }
    async getGenreStats(userId) {
        const watchedMovies = await this.userMovieRepository.find({
            where: { userId, status: user_movie_entity_1.MovieStatus.WATCHED },
            relations: ['movie'],
        });
        const stats = await this.generateWatchedStats(watchedMovies);
        return stats.genresDistribution;
    }
    async getYearStats(userId) {
        return [];
    }
    async getDirectorStats(userId) {
        const watchedMovies = await this.userMovieRepository.find({
            where: { userId, status: user_movie_entity_1.MovieStatus.WATCHED },
            relations: ['movie'],
        });
        const stats = await this.generateWatchedStats(watchedMovies);
        return stats.topDirectors;
    }
    async getAdvancedAnalytics(userId) {
        return this.getCompleteAnalytics(userId);
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