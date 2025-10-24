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
var ListsService_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.ListsService = void 0;
const common_1 = require("@nestjs/common");
const typeorm_1 = require("@nestjs/typeorm");
const typeorm_2 = require("typeorm");
const movie_entity_1 = require("../../database/entities/movie.entity");
const user_movie_entity_1 = require("../../database/entities/user-movie.entity");
let ListsService = ListsService_1 = class ListsService {
    constructor(movieRepository, userMovieRepository) {
        this.movieRepository = movieRepository;
        this.userMovieRepository = userMovieRepository;
        this.logger = new common_1.Logger(ListsService_1.name);
    }
    async createCustomList(name, filters, description) {
        try {
            this.logger.log(`creazione lista personalizzata: ${name} per utente ${filters.userId}`);
            const movies = await this.filterMovies(filters);
            const totalRuntime = movies
                .map((m) => m.runtime || 0)
                .reduce((a, b) => a + b, 0);
            const movieIds = movies.map(m => m.id);
            const userMovies = await this.userMovieRepository.find({
                where: {
                    userId: filters.userId,
                    movieId: movieIds,
                },
            });
            const ratingsWithValues = userMovies
                .map(um => um.userRating || 0)
                .filter(r => r > 0);
            const averageRating = ratingsWithValues.length > 0
                ? ratingsWithValues.reduce((a, b) => a + b, 0) / ratingsWithValues.length
                : undefined;
            return {
                name,
                description,
                movies,
                createdAt: new Date(),
                totalMovies: movies.length,
                totalRuntime,
                averageRating,
            };
        }
        catch (error) {
            this.logger.error(`errore creazione lista: ${error.message}`);
            throw error;
        }
    }
    async filterMovies(filters) {
        try {
            const { userId, status, ...movieFilters } = filters;
            const userMoviesQuery = this.userMovieRepository
                .createQueryBuilder('um')
                .leftJoinAndSelect('um.movie', 'movie')
                .where('um.userId = :userId', { userId });
            if (status) {
                const movieStatus = status === 'watched' ? user_movie_entity_1.MovieStatus.WATCHED : user_movie_entity_1.MovieStatus.WATCHLIST;
                userMoviesQuery.andWhere('um.status = :status', { status: movieStatus });
            }
            const userMovies = await userMoviesQuery.getMany();
            let movies = userMovies.map(um => um.movie);
            if (movieFilters.genre) {
                movies = movies.filter(m => m.genres?.some(g => g.toLowerCase().includes(movieFilters.genre.toLowerCase())));
            }
            if (movieFilters.director) {
                movies = movies.filter(m => m.director?.toLowerCase().includes(movieFilters.director.toLowerCase()));
            }
            if (movieFilters.minYear) {
                movies = movies.filter(m => m.year && m.year >= movieFilters.minYear);
            }
            if (movieFilters.maxYear) {
                movies = movies.filter(m => m.year && m.year <= movieFilters.maxYear);
            }
            if (movieFilters.minRating || movieFilters.maxRating || movieFilters.hasRating) {
                const movieIds = movies.map(m => m.id);
                const userMoviesWithRatings = await this.userMovieRepository.find({
                    where: {
                        userId,
                        movieId: movieIds,
                    },
                });
                const ratingMap = new Map(userMoviesWithRatings.map(um => [um.movieId, um.userRating]));
                movies = movies.filter(m => {
                    const rating = ratingMap.get(m.id);
                    if (movieFilters.hasRating && !rating)
                        return false;
                    if (movieFilters.minRating && (!rating || rating < movieFilters.minRating))
                        return false;
                    if (movieFilters.maxRating && (!rating || rating > movieFilters.maxRating))
                        return false;
                    return true;
                });
            }
            if (movieFilters.sortBy) {
                movies = this.sortMovies(movies, movieFilters.sortBy, movieFilters.sortOrder || 'ASC');
            }
            return movies;
        }
        catch (error) {
            this.logger.error(`errore filtro film: ${error.message}`);
            return [];
        }
    }
    sortMovies(movies, sortBy, sortOrder) {
        const sorted = [...movies].sort((a, b) => {
            let comparison = 0;
            switch (sortBy) {
                case 'title':
                    comparison = a.title.localeCompare(b.title);
                    break;
                case 'year':
                    comparison = (a.year || 0) - (b.year || 0);
                    break;
                case 'runtime':
                    comparison = (a.runtime || 0) - (b.runtime || 0);
                    break;
                case 'rating':
                    comparison = (a.tmdb_rating || 0) - (b.tmdb_rating || 0);
                    break;
                default:
                    comparison = 0;
            }
            return sortOrder === 'DESC' ? -comparison : comparison;
        });
        return sorted;
    }
    async getPresetLists(userId) {
        try {
            const [topRated, recentlyAdded, longestMovies] = await Promise.all([
                this.createCustomList('Top Rated', {
                    userId,
                    minRating: 8,
                    sortBy: 'rating',
                    sortOrder: 'DESC',
                }, 'I tuoi film con il rating più alto'),
                this.createCustomList('Recently Added', {
                    userId,
                    sortBy: 'year',
                    sortOrder: 'DESC',
                }, 'Film aggiunti di recente'),
                this.createCustomList('Longest Movies', {
                    userId,
                    sortBy: 'runtime',
                    sortOrder: 'DESC',
                }, 'I film più lunghi della tua collezione'),
            ]);
            return { topRated, recentlyAdded, longestMovies };
        }
        catch (error) {
            this.logger.error(`errore recupero preset lists: ${error.message}`);
            throw error;
        }
    }
};
exports.ListsService = ListsService;
exports.ListsService = ListsService = ListsService_1 = __decorate([
    (0, common_1.Injectable)(),
    __param(0, (0, typeorm_1.InjectRepository)(movie_entity_1.MovieEntity)),
    __param(1, (0, typeorm_1.InjectRepository)(user_movie_entity_1.UserMovieEntity)),
    __metadata("design:paramtypes", [typeorm_2.Repository,
        typeorm_2.Repository])
], ListsService);
//# sourceMappingURL=lists.service.js.map