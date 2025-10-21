export declare class CreateListDto {
    name: string;
    description?: string;
    movie_ids?: string[];
    target_date?: string;
    frequency?: string;
    is_public?: boolean;
}
export declare class UpdateListDto {
    name?: string;
    description?: string;
    movie_ids?: string[];
    target_date?: string;
    frequency?: string;
    is_public?: boolean;
}
export declare class AddMovieToListDto {
    movie_id: string;
}
