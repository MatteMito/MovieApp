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
let ListsService = ListsService_1 = class ListsService {
    constructor(movieRepository) {
        this.movieRepository = movieRepository;
        this.logger = new common_1.Logger(ListsService_1.name);
    }
    async createCustomList(name, filters, description) {
        try {
            this.logger.log(`creazione lista personalizzata: ${name}`);
            const movies = await this.filterMovies(filters);
            const totalRuntime = movies
                .map((m) => m.runtime || 0)
                .reduce((a, b) => a + b, 0);
            const ratingsWithValues = movies
                .map((m) => m.user_rating || m.tmdb_rating)
                .filter((r) => r !== null && r !== undefined);
            const averageRating = ratingsWithValues.length > 0
                ? ratingsWithValues.reduce((a, b) => a + b, 0) /
                    ratingsWithValues.length
                : undefined;
            const list = {
                name,
                description,
                movies,
                createdAt: new Date(),
                totalMovies: movies.length,
                totalRuntime,
                averageRating,
            };
            this.logger.log(`lista creata: ${movies.length} film`);
            return list;
        }
        catch (error) {
            this.logger.error(`errore creazione lista: ${error.message}`);
            throw error;
        }
    }
    async filterMovies(filters) {
        try {
            let query = this.movieRepository.createQueryBuilder('movie');
            if (filters.genre) {
                query = query.andWhere(':genre = ANY(movie.genres)', {
                    genre: filters.genre,
                });
            }
            if (filters.director) {
                query = query.andWhere('movie.director ILIKE :director', {
                    director: `%${filters.director}%`,
                });
            }
            if (filters.minYear) {
                query = query.andWhere('movie.year >= :minYear', {
                    minYear: filters.minYear,
                });
            }
            if (filters.maxYear) {
                query = query.andWhere('movie.year <= :maxYear', {
                    maxYear: filters.maxYear,
                });
            }
            if (filters.minRating) {
                query = query.andWhere('(movie.user_rating >= :minRating OR movie.tmdb_rating >= :minRating)', { minRating: filters.minRating });
            }
            if (filters.maxRating) {
                query = query.andWhere('(movie.user_rating <= :maxRating OR movie.tmdb_rating <= :maxRating)', { maxRating: filters.maxRating });
            }
            if (filters.watched !== undefined) {
                query = query.andWhere('movie.is_watched = :watched', {
                    watched: filters.watched,
                });
            }
            if (filters.hasRating) {
                query = query.andWhere('(movie.user_rating IS NOT NULL OR movie.tmdb_rating IS NOT NULL)');
            }
            const sortBy = filters.sortBy || 'title';
            const sortOrder = filters.sortOrder || 'ASC';
            switch (sortBy) {
                case 'year':
                    query = query.orderBy('movie.year', sortOrder, 'NULLS LAST');
                    break;
                case 'rating':
                    query = query.orderBy('COALESCE(movie.user_rating, movie.tmdb_rating)', sortOrder, 'NULLS LAST');
                    break;
                case 'runtime':
                    query = query.orderBy('movie.runtime', sortOrder, 'NULLS LAST');
                    break;
                default:
                    query = query.orderBy('movie.title', sortOrder);
            }
            const movies = await query.getMany();
            this.logger.log(`filtro applicato: ${movies.length} film trovati`);
            return movies;
        }
        catch (error) {
            this.logger.error(`errore filtro: ${error.message}`);
            throw error;
        }
    }
    async getTopRatedMovies(limit = 50) {
        try {
            this.logger.log(`recupero top ${limit} film`);
            const movies = await this.movieRepository
                .createQueryBuilder('movie')
                .where('movie.user_rating IS NOT NULL OR movie.tmdb_rating IS NOT NULL')
                .orderBy('COALESCE(movie.user_rating, movie.tmdb_rating)', 'DESC')
                .limit(limit)
                .getMany();
            return this.buildListFromMovies('Top Rated Movies', movies);
        }
        catch (error) {
            this.logger.error(`errore top rated: ${error.message}`);
            throw error;
        }
    }
    async getRecentMovies(limit = 50) {
        try {
            this.logger.log(`recupero ${limit} film recenti`);
            const currentYear = new Date().getFullYear();
            const movies = await this.movieRepository
                .createQueryBuilder('movie')
                .where('movie.year >= :minYear', { minYear: currentYear - 5 })
                .orderBy('movie.year', 'DESC')
                .limit(limit)
                .getMany();
            return this.buildListFromMovies('Recent Movies', movies);
        }
        catch (error) {
            this.logger.error(`errore recent movies: ${error.message}`);
            throw error;
        }
    }
    async getClassicMovies() {
        try {
            this.logger.log('recupero film classici');
            const movies = await this.movieRepository
                .createQueryBuilder('movie')
                .where('movie.year >= :minYear AND movie.year <= :maxYear', {
                minYear: 1950,
                maxYear: 1999,
            })
                .orderBy('movie.year', 'ASC')
                .getMany();
            return this.buildListFromMovies('Classic Movies (1950-1999)', movies);
        }
        catch (error) {
            this.logger.error(`errore classic movies: ${error.message}`);
            throw error;
        }
    }
    async getLongMovies(minRuntime = 180) {
        try {
            this.logger.log(`recupero film lunghi (>${minRuntime}min)`);
            const movies = await this.movieRepository
                .createQueryBuilder('movie')
                .where('movie.runtime >= :minRuntime', { minRuntime })
                .orderBy('movie.runtime', 'DESC')
                .getMany();
            return this.buildListFromMovies(`Long Movies (${minRuntime}+ min)`, movies);
        }
        catch (error) {
            this.logger.error(`errore long movies: ${error.message}`);
            throw error;
        }
    }
    async getMoviesByDecade(decade) {
        try {
            this.logger.log(`recupero film anni ${decade}`);
            const movies = await this.movieRepository
                .createQueryBuilder('movie')
                .where('movie.year >= :startYear AND movie.year < :endYear', {
                startYear: decade,
                endYear: decade + 10,
            })
                .orderBy('movie.year', 'ASC')
                .getMany();
            return this.buildListFromMovies(`Movies from ${decade}s`, movies);
        }
        catch (error) {
            this.logger.error(`errore movies by decade: ${error.message}`);
            throw error;
        }
    }
    async getUnwatchedWatchlist() {
        try {
            this.logger.log('recupero watchlist non vista');
            const movies = await this.movieRepository
                .createQueryBuilder('movie')
                .where('movie.is_watched = :watched', { watched: false })
                .orderBy('movie.title', 'ASC')
                .getMany();
            return this.buildListFromMovies('Watchlist (Unwatched)', movies);
        }
        catch (error) {
            this.logger.error(`errore unwatched watchlist: ${error.message}`);
            throw error;
        }
    }
    async getMoviesByGenre(genre) {
        try {
            this.logger.log(`recupero film genere: ${genre}`);
            const movies = await this.movieRepository
                .createQueryBuilder('movie')
                .where(':genre = ANY(movie.genres)', { genre })
                .orderBy('movie.title', 'ASC')
                .getMany();
            return this.buildListFromMovies(`${genre} Movies`, movies);
        }
        catch (error) {
            this.logger.error(`errore movies by genre: ${error.message}`);
            throw error;
        }
    }
    async getMoviesByDirector(director) {
        try {
            this.logger.log(`recupero film regista: ${director}`);
            const movies = await this.movieRepository
                .createQueryBuilder('movie')
                .where('movie.director ILIKE :director', { director: `%${director}%` })
                .orderBy('movie.year', 'ASC')
                .getMany();
            return this.buildListFromMovies(`Movies by ${director}`, movies);
        }
        catch (error) {
            this.logger.error(`errore movies by director: ${error.message}`);
            throw error;
        }
    }
    buildListFromMovies(name, movies) {
        const totalRuntime = movies
            .map((m) => m.runtime || 0)
            .reduce((a, b) => a + b, 0);
        const ratingsWithValues = movies
            .map((m) => m.user_rating || m.tmdb_rating)
            .filter((r) => r !== null && r !== undefined);
        const averageRating = ratingsWithValues.length > 0
            ? ratingsWithValues.reduce((a, b) => a + b, 0) /
                ratingsWithValues.length
            : undefined;
        return {
            name,
            movies,
            createdAt: new Date(),
            totalMovies: movies.length,
            totalRuntime,
            averageRating,
        };
    }
    async getAllGenres() {
        try {
            this.logger.log('recupero tutti i generi');
            const movies = await this.movieRepository.find();
            const genresSet = new Set();
            movies.forEach((movie) => {
                movie.genres?.forEach((genre) => {
                    if (genre)
                        genresSet.add(genre);
                });
            });
            const genres = Array.from(genresSet).sort();
            this.logger.log(`${genres.length} generi trovati`);
            return genres;
        }
        catch (error) {
            this.logger.error(`errore recupero generi: ${error.message}`);
            throw error;
        }
    }
    async getAllDirectors() {
        try {
            this.logger.log('recupero tutti i registi');
            const movies = await this.movieRepository.find();
            const directorsSet = new Set();
            movies.forEach((movie) => {
                if (movie.director)
                    directorsSet.add(movie.director);
            });
            const directors = Array.from(directorsSet).sort();
            this.logger.log(`${directors.length} registi trovati`);
            return directors;
        }
        catch (error) {
            this.logger.error(`errore recupero registi: ${error.message}`);
            throw error;
        }
    }
};
exports.ListsService = ListsService;
exports.ListsService = ListsService = ListsService_1 = __decorate([
    (0, common_1.Injectable)(),
    __param(0, (0, typeorm_1.InjectRepository)(movie_entity_1.MovieEntity)),
    __metadata("design:paramtypes", [typeorm_2.Repository])
], ListsService);
//# sourceMappingURL=lists.service.js.map