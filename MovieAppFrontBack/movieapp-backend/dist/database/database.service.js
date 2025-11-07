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
var DatabaseService_1;
Object.defineProperty(exports, "__esModule", { value: true });
exports.DatabaseService = void 0;
const common_1 = require("@nestjs/common");
const typeorm_1 = require("@nestjs/typeorm");
const typeorm_2 = require("typeorm");
const movie_entity_1 = require("./entities/movie.entity");
let DatabaseService = DatabaseService_1 = class DatabaseService {
    constructor(movieRepository) {
        this.movieRepository = movieRepository;
        this.logger = new common_1.Logger(DatabaseService_1.name);
        this.analyticsCache = new Map();
    }
    movieToEntity(movie) {
        const entity = new movie_entity_1.MovieEntity();
        entity.id = movie.id;
        entity.title = movie.title;
        entity.year = movie.year;
        entity.source = movie.source;
        entity.tmdb_id = movie.tmdb_id;
        entity.genres = movie.genres?.length > 0 ? movie.genres : [];
        entity.director = movie.director;
        entity.actors = movie.actors?.length > 0 ? movie.actors : undefined;
        entity.overview = movie.overview;
        entity.tagline = movie.tagline;
        entity.poster_url = movie.poster_url;
        entity.backdrop_url = movie.backdrop_url;
        entity.tmdb_rating = movie.tmdb_rating;
        entity.vote_count = movie.vote_count;
        entity.runtime = movie.runtime;
        entity.budget = movie.budget;
        entity.revenue = movie.revenue;
        entity.status = movie.status;
        entity.original_language = movie.original_language;
        entity.original_title = movie.original_title;
        entity.popularity = movie.popularity;
        entity.adult = movie.adult;
        entity.homepage = movie.homepage;
        entity.imdb_id = movie.imdb_id;
        entity.production_companies = movie.production_companies?.length > 0 ? movie.production_companies : [];
        entity.production_countries = movie.production_countries?.length > 0 ? movie.production_countries : [];
        entity.spoken_languages = movie.spoken_languages?.length > 0 ? movie.spoken_languages : [];
        entity.keywords = movie.keywords?.length > 0 ? movie.keywords : [];
        entity.certification = movie.certification;
        entity.trailer_url = movie.trailer_url;
        entity.is_enriched = movie.is_enriched || false;
        return entity;
    }
    entityToMovie(entity) {
        return {
            id: entity.id,
            title: entity.title,
            year: entity.year,
            source: entity.source,
            tmdb_id: entity.tmdb_id,
            director: entity.director,
            genres: entity.genres || [],
            actors: entity.actors || [],
            overview: entity.overview,
            tagline: entity.tagline,
            runtime: entity.runtime,
            poster_url: entity.poster_url,
            backdrop_url: entity.backdrop_url,
            tmdb_rating: entity.tmdb_rating,
            vote_count: entity.vote_count,
            budget: entity.budget,
            revenue: entity.revenue,
            status: entity.status,
            original_language: entity.original_language,
            original_title: entity.original_title,
            popularity: entity.popularity,
            adult: entity.adult,
            homepage: entity.homepage,
            imdb_id: entity.imdb_id,
            production_companies: entity.production_companies || [],
            production_countries: entity.production_countries || [],
            spoken_languages: entity.spoken_languages || [],
            keywords: entity.keywords || [],
            certification: entity.certification,
            trailer_url: entity.trailer_url,
            created_at: entity.created_at,
            updated_at: entity.updated_at,
            is_enriched: entity.is_enriched,
        };
    }
    async saveMovie(movie) {
        try {
            let entity = await this.movieRepository.findOne({
                where: { id: movie.id },
            });
            if (!entity && movie.title && movie.year) {
                entity = await this.movieRepository.findOne({
                    where: {
                        title: movie.title,
                        year: movie.year,
                        source: movie.source,
                    },
                });
                if (entity) {
                    this.logger.log(`film duplicato trovato: ${movie.title} (${movie.year}) - uso id esistente: ${entity.id}`);
                    movie.id = entity.id;
                }
            }
            if (entity) {
                Object.assign(entity, {
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
                    status: movie.status,
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
                });
            }
            else {
                entity = this.movieRepository.create({
                    id: movie.id,
                    title: movie.title,
                    year: movie.year,
                    source: movie.source,
                    tmdb_id: movie.tmdb_id,
                    is_enriched: movie.is_enriched || false,
                    genres: movie.genres || [],
                    director: movie.director,
                    actors: movie.actors || [],
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
                    status: movie.status,
                    production_companies: movie.production_companies || [],
                    production_countries: movie.production_countries || [],
                    original_language: movie.original_language,
                    original_title: movie.original_title,
                    spoken_languages: movie.spoken_languages || [],
                    adult: movie.adult,
                    homepage: movie.homepage,
                    imdb_id: movie.imdb_id,
                    keywords: movie.keywords || [],
                    certification: movie.certification,
                    trailer_url: movie.trailer_url,
                });
            }
            return await this.movieRepository.save(entity);
        }
        catch (error) {
            this.logger.error(`errore save movie: ${error.message}`);
            throw error;
        }
    }
    async saveMovies(movies) {
        try {
            const savedEntities = [];
            for (const movie of movies) {
                const entity = await this.saveMovie(movie);
                savedEntities.push(entity);
            }
            this.logger.log(`salvati ${savedEntities.length} film nel database (con deduplicazione)`);
            return savedEntities;
        }
        catch (error) {
            this.logger.error(`errore salvataggio batch: ${error.message}`);
            throw error;
        }
    }
    async findMovieById(id) {
        try {
            const entity = await this.movieRepository.findOne({ where: { id } });
            return entity ? this.entityToMovie(entity) : null;
        }
        catch (error) {
            this.logger.error(`errore ricerca film ${id}: ${error.message}`);
            return null;
        }
    }
    async findMovieByTitleYear(title, year) {
        try {
            const where = { title };
            if (year) {
                where.year = year;
            }
            const entity = await this.movieRepository.findOne({ where });
            return entity ? this.entityToMovie(entity) : null;
        }
        catch (error) {
            this.logger.error(`errore ricerca ${title}: ${error.message}`);
            return null;
        }
    }
    async findMovieByTmdbId(tmdbId) {
        try {
            const entity = await this.movieRepository.findOne({
                where: { tmdb_id: tmdbId },
            });
            return entity ? this.entityToMovie(entity) : null;
        }
        catch (error) {
            this.logger.error(`errore ricerca tmdb ${tmdbId}: ${error.message}`);
            return null;
        }
    }
    async getAllMovies() {
        try {
            const entities = await this.movieRepository.find({
                order: { title: 'ASC' },
            });
            return entities.map((entity) => this.entityToMovie(entity));
        }
        catch (error) {
            this.logger.error(`errore recupero tutti i film: ${error.message}`);
            throw error;
        }
    }
    async getStats() {
        try {
            const totalMovies = await this.movieRepository.count();
            const enrichedMovies = await this.movieRepository.count({
                where: { is_enriched: true },
            });
            const withTmdbId = await this.movieRepository.count({
                where: { tmdb_id: (0, typeorm_2.Not)((0, typeorm_2.IsNull)()) },
            });
            return {
                totalMovies,
                enrichedMovies,
                notEnriched: totalMovies - enrichedMovies,
                withTmdbId,
                enrichmentRate: totalMovies > 0
                    ? Math.round((enrichedMovies / totalMovies) * 100)
                    : 0,
            };
        }
        catch (error) {
            this.logger.error(`errore stats: ${error.message}`);
            throw error;
        }
    }
    async getSyncStats() {
        return this.getStats();
    }
    async searchMoviesForAutocomplete(query, limit = 10) {
        try {
            const entities = await this.movieRepository
                .createQueryBuilder('movie')
                .where('LOWER(movie.title) LIKE LOWER(:query)', {
                query: `%${query}%`,
            })
                .orderBy('movie.popularity', 'DESC', 'NULLS LAST')
                .addOrderBy('movie.title', 'ASC')
                .limit(limit)
                .getMany();
            return entities.map((entity) => this.entityToMovie(entity));
        }
        catch (error) {
            this.logger.error(`errore autocomplete search: ${error.message}`);
            return [];
        }
    }
    async deleteAllMovies() {
        try {
            await this.movieRepository.clear();
            this.logger.log('tutti i film eliminati dal database');
        }
        catch (error) {
            this.logger.error(`errore eliminazione film: ${error.message}`);
            throw error;
        }
    }
    getCachedAnalytics(key) {
        const cached = this.analyticsCache.get(key);
        if (cached && cached.expiresAt > new Date()) {
            return cached.data;
        }
        return null;
    }
    setCachedAnalytics(key, data, ttlMinutes = 5) {
        const expiresAt = new Date();
        expiresAt.setMinutes(expiresAt.getMinutes() + ttlMinutes);
        this.analyticsCache.set(key, { data, expiresAt });
    }
    clearAnalyticsCache() {
        this.analyticsCache.clear();
    }
};
exports.DatabaseService = DatabaseService;
exports.DatabaseService = DatabaseService = DatabaseService_1 = __decorate([
    (0, common_1.Injectable)(),
    __param(0, (0, typeorm_1.InjectRepository)(movie_entity_1.MovieEntity)),
    __metadata("design:paramtypes", [typeorm_2.Repository])
], DatabaseService);
//# sourceMappingURL=database.service.js.map