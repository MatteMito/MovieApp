export declare class CreateMovieDto {
    title: string;
    year?: number;
    director?: string;
    genres?: string[];
    user_rating?: number;
    date_rated?: string;
    is_watched: boolean;
    source: string;
}
export declare class UpdateMovieDto {
    title?: string;
    year?: number;
    director?: string;
    genres?: string[];
    user_rating?: number;
    date_rated?: string;
    is_watched?: boolean;
}
export declare class EnrichMovieDto {
    id: string;
    title: string;
    year?: number;
    user_rating?: number;
    date_rated?: string;
    is_watched: boolean;
    source: string;
}
export declare class BatchUploadDto {
    watchlist: EnrichMovieDto[];
    watched: EnrichMovieDto[];
}
