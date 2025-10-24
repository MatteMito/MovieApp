import { Repository } from 'typeorm';
import { MovieEntity } from '../../database/entities/movie.entity';
import { UserMovieEntity } from '../../database/entities/user-movie.entity';
export interface MovieList {
    name: string;
    description?: string;
    movies: MovieEntity[];
    createdAt: Date;
    totalMovies: number;
    totalRuntime: number;
    averageRating?: number;
}
export interface ListFilters {
    genre?: string;
    director?: string;
    minYear?: number;
    maxYear?: number;
    minRating?: number;
    maxRating?: number;
    status?: 'watched' | 'watchlist';
    hasRating?: boolean;
    sortBy?: 'title' | 'year' | 'rating' | 'runtime';
    sortOrder?: 'ASC' | 'DESC';
    userId: string;
}
export declare class ListsService {
    private movieRepository;
    private userMovieRepository;
    private readonly logger;
    constructor(movieRepository: Repository<MovieEntity>, userMovieRepository: Repository<UserMovieEntity>);
    createCustomList(name: string, filters: ListFilters, description?: string): Promise<MovieList>;
    private filterMovies;
    private sortMovies;
    getPresetLists(userId: string): Promise<{
        topRated: MovieList;
        recentlyAdded: MovieList;
        longestMovies: MovieList;
    }>;
}
