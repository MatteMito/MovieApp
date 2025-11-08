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
        return { basicStats, watchedStats, ratingStats };
    }
    async generateBasicStats(userId, watchedMovies) {
        const allUserMovies = await this.userMovieRepository.find({
            where: { userId },
            relations: ['movie'],
        });
        const watchlistMovies = allUserMovies.filter(um => um.status === user_movie_entity_1.MovieStatus.WATCHLIST);
        const enrichedMovies = allUserMovies.filter(um => um.movie?.tmdb_id);
        const watchedWithRating = watchedMovies.filter(um => um.userRating != null);
        const averageRating = watchedWithRating.length > 0
            ? watchedWithRating.reduce((sum, um) => sum + um.userRating, 0) / watchedWithRating.length
            : undefined;
        const totalRuntime = watchedMovies.reduce((sum, um) => sum + (um.movie?.runtime || 0), 0);
        const genresSet = new Set();
        watchedMovies.forEach(um => {
            um.movie?.genres?.forEach((g) => genresSet.add(g));
        });
        const directorsSet = new Set();
        watchedMovies.forEach(um => {
            if (um.movie?.director)
                directorsSet.add(um.movie.director);
        });
        return {
            totalMovies: allUserMovies.length,
            watchedCount: watchedMovies.length,
            watchlistCount: watchlistMovies.length,
            enrichedMovies: enrichedMovies.length,
            averageRating: averageRating ? Math.round(averageRating * 10) / 10 : undefined,
            totalRuntime,
            uniqueGenres: genresSet.size,
            uniqueDirectors: directorsSet.size,
        };
    }
    async generateWatchedStats(watchedMovies) {
        const genresDistribution = this.calculateGenresDistribution(watchedMovies);
        const genreCombinations = this.calculateGenreCombinations(watchedMovies);
        const decadeDistribution = this.calculateDecadeDistribution(watchedMovies);
        const topDirectors = this.calculateTopDirectors(watchedMovies);
        const topActors = this.calculateTopActors(watchedMovies);
        const directorActorPairs = this.calculateDirectorActorPairs(watchedMovies);
        const productionStudios = this.calculateProductionStudios(watchedMovies);
        const productionCountries = this.calculateProductionCountries(watchedMovies);
        const continentDistribution = this.calculateContinentDistribution(watchedMovies);
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
        const userRatingsDistribution = this.calculateRatingsDistribution(ratedMovies.map(um => um.userRating));
        const communityRatingsDistribution = this.calculateRatingsDistribution(ratedMovies.map(um => um.movie?.tmdb_rating).filter(r => r));
        const divergentOpinions = this.calculateDivergentOpinions(ratedMovies);
        const avgRatingByDirector = this.calculateAvgRatingByDirector(ratedMovies);
        const avgRatingByActor = this.calculateAvgRatingByActor(ratedMovies);
        const avgRatingByGenre = this.calculateAvgRatingByGenre(ratedMovies);
        const avgRatingByDecade = this.calculateAvgRatingByDecade(ratedMovies);
        const runtimeVsRating = this.calculateRuntimeVsRating(ratedMovies);
        const revenueVsRating = this.calculateRevenueVsRating(ratedMovies);
        return {
            userRatingsDistribution,
            communityRatingsDistribution,
            divergentOpinions,
            avgRatingByDirector,
            avgRatingByActor,
            avgRatingByGenre,
            avgRatingByGenreCombination: [],
            avgRatingByDecade,
            runtimeVsRating,
            revenueVsRating,
        };
    }
    calculateGenresDistribution(movies) {
        const genreMap = new Map();
        const total = movies.length;
        movies.forEach(um => {
            um.movie?.genres?.forEach((genre) => {
                genreMap.set(genre, (genreMap.get(genre) || 0) + 1);
            });
        });
        return Array.from(genreMap.entries())
            .map(([genre, count]) => ({
            genre,
            count,
            percentage: Math.round((count / total) * 100 * 10) / 10,
        }))
            .sort((a, b) => b.count - a.count)
            .slice(0, 10);
    }
    calculateGenreCombinations(movies) {
        const comboMap = new Map();
        movies.forEach(um => {
            const genres = um.movie?.genres;
            if (genres && genres.length > 1) {
                const combo = genres.slice().sort().join(', ');
                comboMap.set(combo, (comboMap.get(combo) || 0) + 1);
            }
        });
        return Object.fromEntries(Array.from(comboMap.entries())
            .sort((a, b) => b[1] - a[1])
            .slice(0, 10));
    }
    calculateDecadeDistribution(movies) {
        const decadeMap = new Map();
        movies.forEach(um => {
            const year = um.movie?.year;
            if (year) {
                const decade = `${Math.floor(year / 10) * 10}s`;
                decadeMap.set(decade, (decadeMap.get(decade) || 0) + 1);
            }
        });
        return Object.fromEntries(Array.from(decadeMap.entries()).sort((a, b) => a[0].localeCompare(b[0])));
    }
    calculateTopDirectors(movies) {
        const directorMap = new Map();
        movies.forEach(um => {
            if (um.movie?.director) {
                directorMap.set(um.movie.director, (directorMap.get(um.movie.director) || 0) + 1);
            }
        });
        return Array.from(directorMap.entries())
            .map(([director, movieCount]) => ({ director, movieCount }))
            .sort((a, b) => b.movieCount - a.movieCount)
            .slice(0, 10);
    }
    calculateTopActors(movies) {
        const actorMap = new Map();
        movies.forEach(um => {
            um.movie?.actors?.forEach((actor) => {
                actorMap.set(actor, (actorMap.get(actor) || 0) + 1);
            });
        });
        return Array.from(actorMap.entries())
            .map(([actor, movieCount]) => ({ actor, movieCount }))
            .sort((a, b) => b.movieCount - a.movieCount)
            .slice(0, 10);
    }
    calculateDirectorActorPairs(movies) {
        const pairMap = new Map();
        movies.forEach(um => {
            if (um.movie?.director && um.movie?.actors) {
                um.movie.actors.forEach((actor) => {
                    const pair = `${um.movie.director}|${actor}`;
                    pairMap.set(pair, (pairMap.get(pair) || 0) + 1);
                });
            }
        });
        return Array.from(pairMap.entries())
            .map(([pair, count]) => {
            const [director, actor] = pair.split('|');
            return { director, actor, movieCount: count };
        })
            .sort((a, b) => b.movieCount - a.movieCount)
            .slice(0, 10);
    }
    calculateProductionStudios(movies) {
        const studioMap = new Map();
        movies.forEach(um => {
            um.movie?.production_companies?.forEach((studio) => {
                studioMap.set(studio, (studioMap.get(studio) || 0) + 1);
            });
        });
        return Array.from(studioMap.entries())
            .map(([studio, count]) => ({ studio, count }))
            .sort((a, b) => b.count - a.count)
            .slice(0, 10);
    }
    calculateProductionCountries(movies) {
        const countryMap = new Map();
        const total = movies.length;
        movies.forEach(um => {
            um.movie?.production_countries?.forEach((country) => {
                countryMap.set(country, (countryMap.get(country) || 0) + 1);
            });
        });
        return Array.from(countryMap.entries())
            .map(([country, count]) => ({
            country,
            count,
            percentage: Math.round((count / total) * 100 * 10) / 10,
        }))
            .sort((a, b) => b.count - a.count);
    }
    calculateContinentDistribution(movies) {
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
        const distribution = {
            'North America': 0,
            'Europe': 0,
            'Asia': 0,
            'South America': 0,
            'Africa': 0,
            'Oceania': 0,
        };
        movies.forEach(um => {
            um.movie?.production_countries?.forEach((country) => {
                const continent = continentMap[country] || 'Other';
                if (distribution[continent] !== undefined) {
                    distribution[continent]++;
                }
            });
        });
        return distribution;
    }
    calculateRatingsDistribution(ratings) {
        const dist = {};
        for (let i = 1; i <= 10; i++) {
            dist[i.toString()] = 0;
        }
        ratings.forEach(rating => {
            const rounded = Math.round(rating);
            if (rounded >= 1 && rounded <= 10) {
                dist[rounded.toString()]++;
            }
        });
        return dist;
    }
    calculateDivergentOpinions(ratedMovies) {
        return ratedMovies
            .filter(um => um.movie?.tmdb_rating)
            .map(um => ({
            movieTitle: um.movie.title,
            userRating: um.userRating,
            tmdbRating: um.movie.tmdb_rating,
            difference: Math.abs(um.userRating - um.movie.tmdb_rating),
        }))
            .sort((a, b) => b.difference - a.difference)
            .slice(0, 10);
    }
    calculateAvgRatingByDirector(ratedMovies) {
        const directorMap = new Map();
        ratedMovies.forEach(um => {
            if (um.movie?.director) {
                const ratings = directorMap.get(um.movie.director) || [];
                ratings.push(um.userRating);
                directorMap.set(um.movie.director, ratings);
            }
        });
        return Array.from(directorMap.entries())
            .filter(([_, ratings]) => ratings.length >= 2)
            .map(([director, ratings]) => ({
            director,
            avgRating: Math.round((ratings.reduce((a, b) => a + b) / ratings.length) * 10) / 10,
        }))
            .sort((a, b) => b.avgRating - a.avgRating)
            .slice(0, 10);
    }
    calculateAvgRatingByActor(ratedMovies) {
        const actorMap = new Map();
        ratedMovies.forEach(um => {
            um.movie?.actors?.forEach((actor) => {
                const ratings = actorMap.get(actor) || [];
                ratings.push(um.userRating);
                actorMap.set(actor, ratings);
            });
        });
        return Array.from(actorMap.entries())
            .filter(([_, ratings]) => ratings.length >= 2)
            .map(([actor, ratings]) => ({
            actor,
            avgRating: Math.round((ratings.reduce((a, b) => a + b) / ratings.length) * 10) / 10,
        }))
            .sort((a, b) => b.avgRating - a.avgRating)
            .slice(0, 10);
    }
    calculateAvgRatingByGenre(ratedMovies) {
        const genreMap = new Map();
        ratedMovies.forEach(um => {
            um.movie?.genres?.forEach((genre) => {
                const ratings = genreMap.get(genre) || [];
                ratings.push(um.userRating);
                genreMap.set(genre, ratings);
            });
        });
        return Array.from(genreMap.entries())
            .map(([genre, ratings]) => ({
            genre,
            avgRating: Math.round((ratings.reduce((a, b) => a + b) / ratings.length) * 10) / 10,
        }))
            .sort((a, b) => b.avgRating - a.avgRating);
    }
    calculateAvgRatingByDecade(ratedMovies) {
        const decadeMap = new Map();
        ratedMovies.forEach(um => {
            const year = um.movie?.year;
            if (year) {
                const decade = `${Math.floor(year / 10) * 10}s`;
                const ratings = decadeMap.get(decade) || [];
                ratings.push(um.userRating);
                decadeMap.set(decade, ratings);
            }
        });
        const result = {};
        decadeMap.forEach((ratings, decade) => {
            result[decade] = Math.round((ratings.reduce((a, b) => a + b) / ratings.length) * 10) / 10;
        });
        return result;
    }
    calculateRuntimeVsRating(ratedMovies) {
        return ratedMovies
            .filter(um => um.movie?.runtime)
            .map(um => ({
            runtime: um.movie.runtime,
            rating: um.userRating,
        }));
    }
    calculateRevenueVsRating(ratedMovies) {
        return ratedMovies
            .filter(um => um.movie?.revenue && um.movie.revenue > 0)
            .map(um => ({
            revenue: um.movie.revenue,
            rating: um.userRating,
        }));
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
        return this.calculateGenresDistribution(watchedMovies);
    }
    async getDirectorStats(userId) {
        const watchedMovies = await this.userMovieRepository.find({
            where: { userId, status: user_movie_entity_1.MovieStatus.WATCHED },
            relations: ['movie'],
        });
        return this.calculateTopDirectors(watchedMovies);
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